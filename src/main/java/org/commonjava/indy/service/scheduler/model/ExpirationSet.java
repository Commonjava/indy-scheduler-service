/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.service.scheduler.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

public class ExpirationSet
        implements Iterable<Expiration>
{
    private Set<Expiration> items;

    public ExpirationSet()
    {
        items = new HashSet<>();
    }

    public ExpirationSet( Expiration... items )
    {
        this.items = new HashSet<>( Arrays.asList( items ) );
    }

    public ExpirationSet( Set<Expiration> items )
    {
        this.items = items;
    }

    public Set<Expiration> getItems()
    {
        return items;
    }

    public void setItems( Set<Expiration> items )
    {
        this.items = items;
    }

    @Override
    public Iterator<Expiration> iterator()
    {
        return items == null ? Collections.emptyIterator() : items.iterator();
    }

    @Override
    public void forEach( Consumer<? super Expiration> action )
    {
        if ( items != null )
        {
            for ( Expiration exp : items )
            {
                action.accept( exp );
            }
        }
    }

    @Override
    public Spliterator<Expiration> spliterator()
    {
        return items == null ? Collections.<Expiration>emptySet().spliterator() : items.spliterator();
    }
}

