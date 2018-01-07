/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util;

import java.util.*;

/**
 * @author George Politis
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V>
{
    /**
     *
     */
    private int cacheSize;

    /**
     * Ctor.
     *
     * @param cacheSize
     */
    public LRUCache(int cacheSize)
    {
        this.cacheSize = cacheSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
    {
        return size() > cacheSize;
    }
}