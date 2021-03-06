/*
 * Copyright 2009 Martin Grotzke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.javakaffee.web.msm.integration;

import static de.javakaffee.web.msm.integration.TestUtils.createCatalina;
import static de.javakaffee.web.msm.integration.TestUtils.createDaemon;
import static de.javakaffee.web.msm.integration.TestUtils.extractNodeId;
import static de.javakaffee.web.msm.integration.TestUtils.get;
import static de.javakaffee.web.msm.integration.TestUtils.getManager;
import static de.javakaffee.web.msm.integration.TestUtils.makeRequest;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.startup.Embedded;
import org.apache.http.HttpException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.thimbleware.jmemcached.CacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;

import de.javakaffee.web.msm.integration.TestUtils.Response;

/**
 * Integration test testing memcached failover.
 *
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 * @version $Id$
 */
public class MemcachedFailoverIntegrationTest {

    private static final Log LOG = LogFactory
            .getLog( MemcachedFailoverIntegrationTest.class );

    private MemCacheDaemon<? extends CacheElement> _daemon1;
    private MemCacheDaemon<? extends CacheElement> _daemon2;
    private MemCacheDaemon<? extends CacheElement> _daemon3;

    private Embedded _tomcat1;

    private int _portTomcat1;

    private DefaultHttpClient _httpClient;

    private String _nodeId1;
    private String _nodeId2;
    private String _nodeId3;

    private InetSocketAddress _address1;

    private InetSocketAddress _address2;

    private InetSocketAddress _address3;

    @BeforeMethod
    public void setUp() throws Throwable {

        _portTomcat1 = 18888;

        _address1 = new InetSocketAddress( "localhost", 21211 );
        _daemon1 = createDaemon( _address1 );
        _daemon1.start();

        _address2 = new InetSocketAddress( "localhost", 21212 );
        _daemon2 = createDaemon( _address2 );
        _daemon2.start();

        _address3 = new InetSocketAddress( "localhost", 21213 );
        _daemon3 = createDaemon( _address3 );
        _daemon3.start();

        _nodeId1 = "n1";
        _nodeId2 = "n2";
        _nodeId3 = "n3";

        try {
            final String memcachedNodes = toString( _nodeId1, _address1 ) +
                " " + toString( _nodeId2, _address2 ) +
                " " + toString( _nodeId3, _address3 );
            _tomcat1 = createCatalina( _portTomcat1, 10, memcachedNodes );
            _tomcat1.start();
        } catch( final Throwable e ) {
            LOG.error( "could not start tomcat.", e );
            throw e;
        }

        _httpClient = new DefaultHttpClient();
    }

    private String toString( final String nodeId, final InetSocketAddress address ) {
        return nodeId + ":" + address.getHostName() + ":" + address.getPort();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if ( _daemon1.isRunning() ) {
            _daemon1.stop();
        }
        if ( _daemon2.isRunning() ) {
            _daemon2.stop();
        }
        if ( _daemon3.isRunning() ) {
            _daemon3.stop();
        }
        _tomcat1.stop();
        _httpClient.getConnectionManager().shutdown();
    }

    /**
     * Tests, that on a memcached failover sessions are relocated to another node and that
     * the session id reflects this.
     */
    @SuppressWarnings("unchecked")
    @Test( enabled = true )
    public void testRelocateSession() throws Throwable {

        final String sid1 = makeRequest( _httpClient, _portTomcat1, null );
        assertNotNull( "No session created.", sid1 );
        final String firstNode = extractNodeId( sid1 );
        assertNotNull( "No node id encoded in session id.", firstNode );

        Thread.sleep( 50 );

        final FailoverInfo info = getFailoverInfo( firstNode );
        info.activeNode.stop();

        Thread.sleep( 50 );

        final String sid2 = makeRequest( _httpClient, _portTomcat1, sid1 );
        final String secondNode = extractNodeId( sid2 );

        assertNotSame( secondNode, firstNode, "First node again selected" );

        assertEquals(
                sid2,
                sid1.substring( 0, sid1.indexOf( "-" ) + 1 ) + secondNode,
                "Unexpected sessionId, sid1: " + sid1 + ", sid2: " + sid2 );

        final Session session = getManager( _tomcat1 ).findSession( sid2 );
        assertNotNull( session, "Session not found by new id " + sid2 );
        assertFalse( session.getNoteNames().hasNext(), "Some notes are set: " + toArray( session.getNoteNames() ) );

    }

    /**
     * Tests that multiple memcached nodes can fail and backup/relocation handles this.
     */
    @SuppressWarnings("unchecked")
    @Test( enabled = true )
    public void testMultipleMemcachedNodesFailure() throws Throwable {

        final String sid1 = makeRequest( _httpClient, _portTomcat1, null );
        assertNotNull( "No session created.", sid1 );
        final String firstNode = extractNodeId( sid1 );
        LOG.debug( "Have firstNode " + firstNode );
        assertNotNull( "No node id encoded in session id.", firstNode );

        /* shutdown active and another memcached node
         */
        final FailoverInfo info = getFailoverInfo( firstNode );
        info.activeNode.stop();
        final Map.Entry<String, MemCacheDaemon<?>> otherNodeWithId = info.otherNode();
        otherNodeWithId.getValue().stop();
        LOG.debug( "Stopping otherNode " + otherNodeWithId.getKey() );

        Thread.sleep( 1000 );

        final String sid2 = makeRequest( _httpClient, _portTomcat1, sid1 );
        final String secondNode = extractNodeId( sid2 );
        LOG.debug( "Have secondNode " + secondNode );
        final String expectedNode = info.otherNodeExcept( otherNodeWithId.getKey() ).getKey();

        assertEquals( secondNode, expectedNode, "Unexpected nodeId: " + secondNode + "." );

        assertEquals(
                sid2,
                sid1.substring( 0, sid1.indexOf( "-" ) + 1 ) + expectedNode,
                "Unexpected sessionId, sid1: " + sid1 + ", sid2: " + sid2 );

        final Session session = getManager( _tomcat1 ).findSession( sid2 );
        assertFalse( session.getNoteNames().hasNext(), "Some notes are set: " + toArray( session.getNoteNames() ) );

    }

    private Set<String> toArray( final Iterator<String> noteNames ) {
        final Set<String> result = new HashSet<String>();
        while ( noteNames.hasNext() ) {
            result.add( noteNames.next() );
        }
        return result;
    }

    /**
     * Tests that the previous session id is kept when all memcached nodes fail.
     *
     * @throws Throwable
     */
    @SuppressWarnings("unchecked")
    @Test( enabled = true )
    public void testAllMemcachedNodesFailure() throws Throwable {

        final String sid1 = makeRequest( _httpClient, _portTomcat1, null );
        assertNotNull( "No session created.", sid1 );

        /* shutdown all memcached nodes
         */
        _daemon1.stop();
        _daemon2.stop();
        _daemon3.stop();

        final String sid2 = makeRequest( _httpClient, _portTomcat1, sid1 );

        assertEquals( sid1, sid2, "SessionId changed." );

        assertNotNull( getSessions().get( sid1 ), "Session "+ sid1 +" not existing." );

        final Session session = getManager( _tomcat1 ).findSession( sid2 );
        assertFalse( session.getNoteNames().hasNext(), "Some notes are set: " + toArray( session.getNoteNames() ) );

    }

    @Test( enabled = true )
    public void testCookieNotSetWhenAllMemcachedsDownIssue40() throws IOException, HttpException {
        /* shutdown all memcached nodes
         */
        _daemon1.stop();
        _daemon2.stop();
        _daemon3.stop();

        final Response response1 = get( _httpClient, _portTomcat1, null );
        final String sessionId = response1.getSessionId();
        assertNotNull( sessionId );
        assertNotNull( response1.getResponseSessionId() );

        final String nodeId = extractNodeId( response1.getResponseSessionId() );
        assertNull( nodeId, "NodeId should be null, but is " + nodeId + "." );

        final Response response2 = get( _httpClient, _portTomcat1, sessionId );
        assertEquals( response2.getSessionId(), sessionId, "SessionId changed" );
        assertNull( response2.getResponseSessionId() );

    }

    @Test( enabled = true )
    public void testCookieNotSetWhenRegularMemcachedDownIssue40() throws Exception {

        /* reconfigure tomcat with failover node
         */
        final String memcachedNodes = toString( _nodeId1, _address1 ) +
        " " + toString( _nodeId2, _address2 );
        restartTomcat( memcachedNodes, _nodeId1 );

        /* shutdown regular memcached node
         */
        _daemon2.stop();

        final Response response1 = get( _httpClient, _portTomcat1, null );
        final String sessionId = response1.getSessionId();
        assertNotNull( sessionId );
        assertNotNull( response1.getResponseSessionId() );

        final String nodeId = extractNodeId( response1.getResponseSessionId() );
        assertEquals( nodeId, _nodeId1 );

        final Response response2 = get( _httpClient, _portTomcat1, sessionId );
        assertEquals( response2.getSessionId(), sessionId, "SessionId changed" );
        assertNull( response2.getResponseSessionId() );

    }

    @Test( enabled = true )
    public void testReconfigureMemcachedNodesAtRuntimeFeature46() throws Exception {

        /* reconfigure tomcat with only two memcached nodes
         */
        final String memcachedNodes1 = toString( _nodeId1, _address1 ) +
        " " + toString( _nodeId2, _address2 );
        restartTomcat( memcachedNodes1, _nodeId2 );

        final Response response1 = get( _httpClient, _portTomcat1, null );
        final String sessionId1 = response1.getSessionId();
        assertNotNull( sessionId1 );
        assertEquals( extractNodeId( sessionId1 ), _nodeId1 );

        /* reconfigure tomcat with only third memcached nodes and stop
         * the first one
         */
        final String memcachedNodes2 = toString( _nodeId1, _address1 ) +
            " " + toString( _nodeId2, _address2 ) +
            " " + toString( _nodeId3, _address3 );
        getManager( _tomcat1 ).setMemcachedNodes( memcachedNodes2 );

        _daemon1.stop();

        Thread.sleep( 1000 );

        /* Expect relocation to node3
         */
        final Response response2 = get( _httpClient, _portTomcat1, sessionId1 );
        assertNotSame( response2.getSessionId(), sessionId1 );
        final String sessionId2 = response2.getResponseSessionId();
        assertNotNull( sessionId2 );
        assertEquals( extractNodeId( sessionId2 ), _nodeId3 );

    }

    @Test( enabled = true )
    public void testReconfigureFailoverNodesAtRuntimeFeature46() throws Exception {

        /* set failover nodes n2 and n3
         */
        getManager( _tomcat1 ).setFailoverNodes( _nodeId2 + " " + _nodeId3 );
        final Response response1 = get( _httpClient, _portTomcat1, null );
        final String sessionId1 = response1.getSessionId();
        assertNotNull( sessionId1 );
        assertEquals( extractNodeId( sessionId1 ), _nodeId1 );

        /* set failover nodes n1 and n2
         */
        getManager( _tomcat1 ).setFailoverNodes( _nodeId1 + " " + _nodeId2 );
        // we need to use another http client, otherwise there's no response cookie.
        final Response response2 = get( new DefaultHttpClient(), _portTomcat1, null );
        final String sessionId2 = response2.getSessionId();
        assertNotNull( sessionId2 );
        assertEquals( extractNodeId( sessionId2 ), _nodeId3 );

    }

    private void restartTomcat( final String memcachedNodes, final String failoverNodes ) throws Exception {
        _tomcat1.stop();
        Thread.sleep( 500 );
        _tomcat1 = createCatalina( _portTomcat1, 10, memcachedNodes );
        getManager( _tomcat1 ).setFailoverNodes( failoverNodes );
        _tomcat1.start();
    }

    private Map<String, Session> getSessions() throws NoSuchFieldException,
            IllegalAccessException {
        final Field field = ManagerBase.class.getDeclaredField( "sessions" );
        field.setAccessible( true );
        @SuppressWarnings("unchecked")
        final Map<String,Session> sessions = (Map<String, Session>)field.get( getManager( _tomcat1 ) );
        return sessions;
    }

    /* plain stupid
     */
    private FailoverInfo getFailoverInfo( final String nodeId ) {
        if ( _nodeId1.equals( nodeId ) ) {
            return new FailoverInfo( _daemon1, asMap( _nodeId2, _daemon2, _nodeId3, _daemon3 ) );
        } else if ( _nodeId2.equals( nodeId ) ) {
            return new FailoverInfo( _daemon2, asMap( _nodeId3, _daemon3, _nodeId1, _daemon1 ) );
        } else if ( _nodeId3.equals( nodeId ) ) {
            return new FailoverInfo( _daemon3, asMap( _nodeId1, _daemon1, _nodeId2, _daemon2 ) );
        }
        throw new IllegalArgumentException( "Node " + nodeId + " is not a valid node id." );
    }

    private Map<String, MemCacheDaemon<?>> asMap( final String nodeId1, final MemCacheDaemon<?> daemon1,
            final String nodeId2, final MemCacheDaemon<?> daemon2 ) {
        final Map<String, MemCacheDaemon<?>> result = new HashMap<String, MemCacheDaemon<?>>( 2 );
        result.put( nodeId1, daemon1 );
        result.put( nodeId2, daemon2 );
        return result;
    }

    static class FailoverInfo {
        MemCacheDaemon<?> activeNode;
        Map<String, MemCacheDaemon<?>> otherNodes;
        public FailoverInfo(final MemCacheDaemon<?> first,
                final Map<String, MemCacheDaemon<?>> otherNodes ) {
            this.activeNode = first;
            this.otherNodes = otherNodes;
        }
        public Entry<String, MemCacheDaemon<?>> otherNode() {
            return otherNodes.entrySet().iterator().next();
        }
        public Entry<String, MemCacheDaemon<?>> otherNodeExcept( final String key ) {
            for( final Map.Entry<String, MemCacheDaemon<?>> entry : otherNodes.entrySet() ) {
                if ( !entry.getKey().equals( key ) ) {
                    return entry;
                }
            }
            throw new IllegalStateException();
        }
    }

}
