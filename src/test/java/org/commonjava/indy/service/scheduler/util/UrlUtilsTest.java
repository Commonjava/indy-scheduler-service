/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
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
package org.commonjava.indy.service.scheduler.util;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static java.util.Map.of;
import static org.commonjava.indy.service.scheduler.util.UrlUtils.buildUrl;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UrlUtilsTest
{
    @Test
    public void testBuildUrl()
            throws Exception
    {
        // formats
        assertThat( buildUrl( "http://a/b/c" ), is( "http://a/b/c" ) );
        assertThat( buildUrl( "http://a/b/c/" ), is( "http://a/b/c/" ) );
        assertThat( buildUrl( "http://a/", "/b", "/c" ), is( "http://a/b/c" ) );
        assertThat( buildUrl( "http://a/", " ", " /b", "/c" ), is( "http://a/b/c" ) );
        assertThat( buildUrl( "http://a/", of( "p1", "d", "p2", "e" ), " ", " /b", "/c" ),
                    allOf( startsWith( "http://a/b/c" ), containsString( "?" ), containsString( "p1=d" ),
                           containsString( "&" ), containsString( "p2=e" ) ) );
        // protocols
        assertThat( "http://a/b/c", is( buildUrl( "http://a", "b", "c" ) ) );
        assertThat( "https://a/b/c", is( buildUrl( "https://a", "b", "c" ) ) );
        assertThat( "ftp://a/b/c", is( buildUrl( "ftp://a", "b", "c" ) ) );
        assertThrows( MalformedURLException.class, () -> buildUrl( "rdp://a", "b", "c" ) );
    }
}
