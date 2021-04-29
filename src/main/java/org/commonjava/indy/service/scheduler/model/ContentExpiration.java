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
package org.commonjava.indy.service.scheduler.model;

public class ContentExpiration
{

    private String key;

    private String path;

    protected ContentExpiration()
    {
    }

    public ContentExpiration( final String key, final String path )
    {
        this.key = key;
        this.path = path;
    }

    public String getKey()
    {
        return key;
    }

    public String getPath()
    {
        return path;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( key == null ) ? 0 : key.hashCode() );
        result = prime * result + ( ( path == null ) ? 0 : path.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final ContentExpiration other = (ContentExpiration) obj;
        if ( key == null )
        {
            if ( other.key != null )
            {
                return false;
            }
        }
        else if ( !key.equals( other.key ) )
        {
            return false;
        }
        if ( path == null )
        {
            return other.path == null;
        }
        else
        {
            return path.equals( other.path );
        }
    }

    protected void setKey( final String key )
    {
        this.key = key;
    }

    protected void setPath( final String path )
    {
        this.path = path;
    }

}
