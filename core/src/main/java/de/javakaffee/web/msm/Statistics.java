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
package de.javakaffee.web.msm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 */
public class Statistics {

    private final AtomicLong _numRequestsWithoutSession = new AtomicLong();
    private final AtomicLong _numRequestsWithTomcatFailover = new AtomicLong();
    private final AtomicLong _numRequestsWithSession = new AtomicLong();
    private final AtomicLong _numRequestsWithBackup = new AtomicLong();
    private final AtomicLong _numRequestsWithMemcachedFailover = new AtomicLong();
    private final AtomicLong _numRequestsWithBackupFailure = new AtomicLong();
    private final AtomicLong _numRequestsWithoutSessionAccess = new AtomicLong();
    private final AtomicLong _numRequestsWithoutAttributesAccess = new AtomicLong();
    private final AtomicLong _numRequestsWithoutSessionModification = new AtomicLong();
    private final AtomicLong _numSessionsLoadedFromMemcached = new AtomicLong();

    private final MinMaxAvgProbe _effectiveBackupProbe = new MinMaxAvgProbe();
    private final MinMaxAvgProbe _backupProbe = new MinMaxAvgProbe();
    private final MinMaxAvgProbe _attributesSerializationProbe = new MinMaxAvgProbe();
    private final MinMaxAvgProbe _memcachedUpdateProbe = new MinMaxAvgProbe();
    private final MinMaxAvgProbe _loadFromMemcachedProbe = new MinMaxAvgProbe();
    private final MinMaxAvgProbe _cachedDataSizeProbe = new MinMaxAvgProbe();

    private Statistics() {
    }

    /**
     * Creates a new (enabled) {@link Statistics} instance.
     * @return a new instance.
     */
    public static Statistics create() {
        return create( true );
    }

    /**
     * Creates a new {@link Statistics} instance which either actually gathers
     * statistics or a dummy {@link Statistics} object that discards all data.
     *
     * @param enabled specifies if stats shall be gathered or discarded.
     * @return a new {@link Statistics} instance
     */
    public static Statistics create( final boolean enabled ) {
        return enabled ? new Statistics() : DISABLED_STATS;
    }

    public void requestWithoutSession() {
        _numRequestsWithoutSession.incrementAndGet();
    }
    public long getRequestsWithoutSession() {
        return _numRequestsWithoutSession.get();
    }
    public void requestWithSession() {
        _numRequestsWithSession.incrementAndGet();
    }
    public long getRequestsWithSession() {
        return _numRequestsWithSession.get();
    }
    public void requestWithBackup() {
        _numRequestsWithBackup.incrementAndGet();
    }
    public long getRequestsWithBackup() {
        return _numRequestsWithBackup.get();
    }
    public void requestWithTomcatFailover() {
        _numRequestsWithTomcatFailover.incrementAndGet();
    }
    public long getRequestsWithTomcatFailover() {
        return _numRequestsWithTomcatFailover.get();
    }
    public void requestWithMemcachedFailover() {
        _numRequestsWithMemcachedFailover.incrementAndGet();
    }
    public long getRequestsWithMemcachedFailover() {
        return _numRequestsWithMemcachedFailover.get();
    }
    public void requestWithBackupFailure() {
        _numRequestsWithBackupFailure.incrementAndGet();
    }
    public long getRequestsWithBackupFailure() {
        return _numRequestsWithBackupFailure.get();
    }
    public void requestWithoutSessionAccess() {
        _numRequestsWithoutSessionAccess.incrementAndGet();
    }
    public long getRequestsWithoutSessionAccess() {
        return _numRequestsWithoutSessionAccess.get();
    }
    public void requestWithoutAttributesAccess() {
        _numRequestsWithoutAttributesAccess.incrementAndGet();
    }
    public long getRequestsWithoutAttributesAccess() {
        return _numRequestsWithoutAttributesAccess.get();
    }
    public void requestWithoutSessionModification() {
        _numRequestsWithoutSessionModification.incrementAndGet();
    }
    public long getRequestsWithoutSessionModification() {
        return _numRequestsWithoutSessionModification.get();
    }
    public void sessionLoadedFromMemcached() {
        _numSessionsLoadedFromMemcached.incrementAndGet();
    }
    public long getSessionsLoadedFromMemcached() {
        return _numSessionsLoadedFromMemcached.get();
    }

    /**
     * Provides info regarding the effective time that was required for session
     * backup in the request thread and it's measured for every request with a session,
     * even if the session id has not set memcached id (this is the time that was effectively
     * required as part of the client request). It should differ from {@link #getBackupProbe()}
     * if async session backup shall be done.
     *
     * @return the effectiveBackupProbe
     * @see BackupSessionService#backupSession(MemcachedBackupSession, boolean)
     */
    public MinMaxAvgProbe getEffectiveBackupProbe() {
        return _effectiveBackupProbe;
    }

    /**
     * Provides info regarding the time that was required for session backup,
     * excluding skipped backups and excluding backups where a session was relocated.
     * @return the backupProbe
     */
    public MinMaxAvgProbe getBackupProbe() {
        return _backupProbe;
    }

    /**
     * @return the attributesSerializationProbe
     */
    public MinMaxAvgProbe getAttributesSerializationProbe() {
        return _attributesSerializationProbe;
    }

    /**
     * @return the storeInMemcachedProbe
     */
    public MinMaxAvgProbe getMemcachedUpdateProbe() {
        return _memcachedUpdateProbe;
    }

    /**
     * @return the loadFromMemcachedProbe
     */
    public MinMaxAvgProbe getLoadFromMemcachedProbe() {
        return _loadFromMemcachedProbe;
    }

    /**
     * @return the sessionSizeProbe
     */
    public MinMaxAvgProbe getCachedDataSizeProbe() {
        return _cachedDataSizeProbe;
    }

    public static class MinMaxAvgProbe {

        private boolean _first = true;
        private final AtomicInteger _count = new AtomicInteger();
        private long _min;
        private long _max;
        private double _avg;

        /**
         * A utility method that calculates the difference of the time
         * between the given <code>startInMillis</code> and {@link System#currentTimeMillis()}
         * and registers the difference via {@link #register(long)}.
         * @param startInMillis the time in millis that shall be subtracted from {@link System#currentTimeMillis()}.
         */
        public void registerSince( final long startInMillis ) {
            register( System.currentTimeMillis() - startInMillis );
        }

        /**
         * Register the given value.
         * @param value the value to register.
         */
        public void register( final long value ) {
            if ( value < _min || _first ) {
                _min = value;
            }
            if ( value > _max || _first ) {
                _max = value;
            }
            _avg = ( _avg * _count.get() + value ) / _count.incrementAndGet();
            _first = false;
        }

        /**
         * @return the count
         */
        int getCount() {
            return _count.get();
        }

        /**
         * @return the min
         */
        long getMin() {
            return _min;
        }

        /**
         * @return the max
         */
        long getMax() {
            return _max;
        }

        /**
         * @return the avg
         */
        double getAvg() {
            return _avg;
        }

        /**
         * Returns a string array with labels and values of count, min, avg and max.
         * @return a String array.
         */
        public String[] getInfo() {
            return new String[] {
                    "Count = " + _count.get(),
                    "Min = "+ _min,
                    "Avg = "+ _avg,
                    "Max = "+ _max
            };
        }

    }

    private static final MinMaxAvgProbe DISABLED_PROBE = new MinMaxAvgProbe() {
        public void registerSince( final long startInMillis ) {};
        public void register( final long value ) {};
    };

    private static final Statistics DISABLED_STATS = new Statistics() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithBackup() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithBackupFailure() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithoutSession() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithoutSessionAccess() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithoutSessionModification() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithSession() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void sessionLoadedFromMemcached() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithMemcachedFailover() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestWithTomcatFailover() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MinMaxAvgProbe getAttributesSerializationProbe() {
            return DISABLED_PROBE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MinMaxAvgProbe getBackupProbe() {
            return DISABLED_PROBE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MinMaxAvgProbe getLoadFromMemcachedProbe() {
            return DISABLED_PROBE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MinMaxAvgProbe getMemcachedUpdateProbe() {
            return DISABLED_PROBE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MinMaxAvgProbe getCachedDataSizeProbe() {
            return DISABLED_PROBE;
        }

    };

}
