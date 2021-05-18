package org.commonjava.indy.service.scheduler.data;

import org.commonjava.indy.service.scheduler.data.ispn.ISPNScheduleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduleManagerUtils
{

    public static String groupName( final String key, final String jobType )
    {
        return key + groupNameSuffix( jobType );
    }

    public static String groupNameSuffix( final String jobType )
    {
        return "#" + jobType;
    }

    @Deprecated
    public static String keyFrom( final String group )
    {
        final String[] parts = group.split( "#" );
        if ( parts.length > 1 )
        {
            final Logger logger = LoggerFactory.getLogger( ISPNScheduleManager.class );
            String key = null;
            try
            {
                key = parts[0];
            }
            catch ( IllegalArgumentException e )
            {
                logger.warn( "Not a store key for string: {}", parts[0] );
            }

            //TODO this part of code may be obsolete, will need further check then remove
            if ( key == null )
            {
                logger.info( "Not a store key for string: {}, will parse as store type", parts[0] );
                final String type = parts[0];
                if ( type != null )
                {
                    key = String.format( "%s:%s:%s", "maven", type, parts[1] );
                }
            }
            return key;
        }

        return null;
    }

}
