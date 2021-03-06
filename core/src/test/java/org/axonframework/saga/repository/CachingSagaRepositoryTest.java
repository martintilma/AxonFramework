/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga.repository;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.jcache.JCache;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.junit.*;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CachingSagaRepositoryTest {

    private JCache associationsCache;
    private JCache sagaCache;
    private SagaRepository repository;
    private CachingSagaRepository testSubject;
    private CacheManager cacheManager;

    @Before
    public void setUp() throws Exception {
        final Cache cch = new Cache("test", 100, false, false, 10, 10);
        cacheManager = CacheManager.create();
        cacheManager.addCache(cch);
        final JCache cache = new JCache(cch);
        associationsCache = spy(cache);
        sagaCache = spy(cache);
        repository = mock(SagaRepository.class);
        testSubject = new CachingSagaRepository(repository, associationsCache, sagaCache);
    }

    @After
    public void tearDown() throws Exception {
        cacheManager.shutdown();
    }

    @Test
    public void testSagaAddedToCacheOnAdd() throws Exception {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);

        verify(sagaCache).put("id", saga);
        verify(associationsCache, never()).put(any(), any());
        verify(repository).add(saga);
    }

    @Test
    public void testAssociationsAddedToCacheOnLoad() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        sagaCache.clear();
        associationsCache.clear();
        reset(sagaCache, associationsCache);

        final AssociationValue associationValue = new AssociationValue("key", "value");
        when(repository.find(StubSaga.class, associationValue)).thenReturn(Collections.singleton("id"));

        Set<String> actual = testSubject.find(StubSaga.class, associationValue);
        assertEquals(actual, singleton("id"));
        verify(associationsCache, atLeast(1)).get("org.axonframework.saga.repository.StubSaga/key=value");
        verify(associationsCache).put("org.axonframework.saga.repository.StubSaga/key=value",
                                      Collections.singleton("id"));
    }

    @Test
    public void testSagaAddedToCacheOnLoad() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        sagaCache.clear();
        associationsCache.clear();
        reset(sagaCache, associationsCache);

        when(repository.load("id")).thenReturn(saga);

        Saga actual = testSubject.load("id");
        assertSame(saga, actual);
        verify(sagaCache).get("id");
        verify(sagaCache).put("id", saga);
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    public void testCommitDelegatedAfterAddingToCache() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        sagaCache.clear();
        associationsCache.clear();
        reset(sagaCache, associationsCache);

        saga.associate("new", "id");
        saga.removeAssociationValue("key", "value");
        testSubject.commit(saga);

        verify(repository).commit(saga);
        verify(associationsCache, never()).put(any(), any());
    }
}
