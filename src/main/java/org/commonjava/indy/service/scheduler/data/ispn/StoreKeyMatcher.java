/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.service.scheduler.data.ispn;

import org.commonjava.indy.service.scheduler.data.ScheduleManagerUtils;
import org.commonjava.indy.service.scheduler.data.cache.CacheHandle;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.commonjava.indy.service.scheduler.model.ScheduleValue;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A key matcher which is used to match the cache key with store key.
 *
 */
public class StoreKeyMatcher
        implements CacheKeyMatcher<ScheduleKey>
{

    //TODO: will have a thought to replace this type of matcher with a ISPN query API in the future to get better performance.

    private final String groupName;

    private final SearchSession searchSession;

    public StoreKeyMatcher( final String storeKey, final String eventType, final SearchSession searchSession )
    {
        assert storeKey != null;
        assert searchSession != null;
        this.groupName = ScheduleManagerUtils.groupName( storeKey, eventType );
        this.searchSession = searchSession;
    }

    @Override
    public Set<ScheduleKey> matches( CacheHandle<ScheduleKey, ?> cacheHandle )
    {
        List<ScheduleValue> list = searchSession.search( ScheduleValue.class )
                                                .where( f -> f.simpleQueryString()
                                                              .field( "key.groupName" )
                                                              .matching( groupName ) )
                                                .fetchAllHits();
        return list.stream().map( ScheduleValue::getKey ).collect( Collectors.toSet() );
    }
}
