package net.apnic.rdap.ip.filters;

import net.apnic.rdap.authority.RDAPAuthority;
import net.apnic.rdap.directory.Directory;
import net.apnic.rdap.error.MalformedRequestException;
import net.apnic.rdap.filter.filters.RDAPPathRouteFilter;
import net.apnic.rdap.filter.RDAPRequestPath;
import net.apnic.rdap.filter.RDAPRequestType;
import net.apnic.rdap.resource.ResourceNotFoundException;

import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceType;

/**
 * Filter for handling ip path segments in RDAP requests.
 */
public class IPRouteFilter
    extends RDAPPathRouteFilter
{
    /**
     * Main constructor which takes the Directory to use for locating ip
     * authorities.
     *
     * @param directory
     * @see RDAPPathRouteFilter
     */
    public IPRouteFilter(Directory directory)
    {
        super(directory);
    }

    /**
     * Main run method for filter which takes the incoming requests and find the
     * ip authority.
     *
     * @see RDAPPathRouteFilter
     */
    @Override
    public RDAPAuthority runRDAPFilter(RDAPRequestPath path)
        throws ResourceNotFoundException, MalformedRequestException
    {
        String[] args = path.getRequestParams();

        if(args.length == 0 || args.length > 2)
        {
            throw new MalformedRequestException(
                "Not enough arguments for ip path segment");
        }

        try
        {
            IpAddress address = IpAddress.parse(args[0]);
            int prefixLength = address.getType() == IpResourceType.IPv4 ?
                IpResourceType.IPv4.getBitSize() :
                IpResourceType.IPv6.getBitSize();

            if(args.length == 2)
            {
                prefixLength = Integer.parseInt(args[1]);
            }

            return getDirectory().getIPAuthority(
                IpRange.prefix(address, prefixLength));
        }
        catch(NumberFormatException ex)
        {
            throw new MalformedRequestException(ex);
        }
        catch(IllegalArgumentException ex)
        {
            throw new MalformedRequestException(ex);
        }
    }

    /**
     * {@inheritDocs}
     */
    @Override
    public RDAPRequestType supportedRequestType()
    {
        return RDAPRequestType.IP;
    }
}
