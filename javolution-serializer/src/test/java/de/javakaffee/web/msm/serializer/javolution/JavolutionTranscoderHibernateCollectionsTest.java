/*
 * Copyright 2010 Martin Grotzke
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
package de.javakaffee.web.msm.serializer.javolution;

import org.testng.annotations.Test;

import de.javakaffee.web.msm.MemcachedBackupSessionManager;
import de.javakaffee.web.msm.serializer.hibernate.AbstractHibernateCollectionsTest;

/**
 * Test for serialization/deserialization of hibernate collection mappings with
 * {@link JavolutionTranscoder}, using {@link HibernateCollectionsXMLFormat}.
 *
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 */
@Test
public class JavolutionTranscoderHibernateCollectionsTest extends AbstractHibernateCollectionsTest {

    @Override
    protected JavolutionTranscoder createTranscoder( final MemcachedBackupSessionManager manager ) {
        return new JavolutionTranscoder( manager, false, new HibernateCollectionsXMLFormat() );
    }

}
