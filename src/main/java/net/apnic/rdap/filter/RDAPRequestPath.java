package net.apnic.rdap.filter;

import java.util.Arrays;

/**
 * Helper class that takes a request path sent to this RDAP server and provides
 * additional more context so further passing can be done.
 */
public class RDAPRequestPath
{
    public static final String PATH_DELIM_REGEX = "\\/";
    public static final char PATH_SEPARATOR = '/';

    private String[] pathParts = null;
    private String requestPath = null;
    private RDAPRequestType requestType = null;

    /**
     * Constructs a new RDAPRequestPath for a supplied request path.
     *
     * Constructor validates the path against a basic set of constraints that
     * all RDAP path segments must follow.
     *
     * @param requestPath Raw request path recieved for an RDAP path segment
     * @throws IllegalArgumentException If the supplied requestPath does not
     *                                  basically conform to an RDAP path
     *                                  segment
     * @see createRequestPath()
     */
    private RDAPRequestPath(String requestPath)
    {
        if(requestPath == null || requestPath.isEmpty())
        {
            throw new IllegalArgumentException(
                "requestPath cannot be null or empty");
        }

        if(requestPath.charAt(0) == PATH_SEPARATOR)
        {
            requestPath = requestPath.substring(1);
        }

        this.requestPath = requestPath;
        pathParts = requestPath.split(PATH_DELIM_REGEX);

        if(pathParts.length < 1)
        {
            throw new IllegalArgumentException(
                "requestPath does not have enough fields");
        }

        setRequestType();
    }

    /**
     *
     */
    public static RDAPRequestPath createRequestPath(String requestPath)
    {
        return new RDAPRequestPath(requestPath);
    }

    public String getPath()
    {
        return requestPath;
    }

    public String[] getRequestParams()
    {
        return Arrays.copyOfRange(pathParts, 2, pathParts.length);
    }

    public RDAPRequestType getRequestType()
    {
        return requestType;
    }

    private void setRequestType()
    {
        requestType = RDAPRequestType.getEnum(pathParts[0]);
    }
}
