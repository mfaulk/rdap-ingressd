package net.apnic.rdap.iana.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.apnic.rdap.authority.RDAPAuthority;
import net.apnic.rdap.authority.RDAPAuthorityStore;
import net.apnic.rdap.autnum.AsnRange;
import net.apnic.rdap.domain.Domain;
import net.apnic.rdap.resource.store.ResourceStore;
import net.apnic.rdap.scraper.Scraper;

import net.ripe.ipresource.IpRange;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Scraper for IANA bootstrap service.
 *
 * @see RFC 7484
 */
public class IANABootstrapScraper
    implements Scraper
{
    /**
     * Lambda callback signature for mapping of authorities on to discovered
     * bootstrap results.
     */
    private interface ResourceMapper
    {
        public void process(RDAPAuthority authority, BootstrapService service);
    }

    public static final String BASE_URI_STR = "https://data.iana.org./rdap/";
    public static final List<String> SUPPORTED_VERSIONS = Arrays.asList("1.0");

    private static final Logger LOGGER =
        Logger.getLogger(IANABootstrapScraper.class.getName());

    private URI asnURI = null;
    private URI domainURI = null;
    private URI ipv4URI = null;
    private URI ipv6URI = null;
    private HttpHeaders requestHeaders = null;
    private RestTemplate restClient = null;

    /**
     * Constructor for creating an IANA bootstrap scraper.
     */
    public IANABootstrapScraper()
    {
        this(BASE_URI_STR);
    }

    /**
     * Constructor for creating IANA bootstrap scraper with non default
     * base URI.
     */
    public IANABootstrapScraper(String baseUri)
    {
        setupURIs(baseUri);
        restClient = new RestTemplate();
        setupRequestHeaders();
    }

    /**
     * {@inheritDocs}
     */
    @Override
    public String getName()
    {
        return "iana-bootstrap-scraper";
    }

    /**
     * Sets up common HTTP headers used in every request to the IANA bootstrap
     * service.
     */
    private void setupRequestHeaders()
    {
        requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        requestHeaders.add(HttpHeaders.USER_AGENT, "");
    }

    /**
     * Main scraper method that dispatches a one-time scrape of IANA.
     *
     * This method is triggered by a scraper scheduler.
     *
     * @see net.apnic.rdap.scraper.ScraperScheduler
     */
    @Override
    public CompletableFuture<Void> start(ResourceStore store,
                                         RDAPAuthorityStore authorityStore)
    {
        return CompletableFuture.allOf(updateASNData(store, authorityStore),
                                       updateDomainData(store, authorityStore),
                                       updateIPv4Data(store, authorityStore), 
                                       updateIPv6Data(store, authorityStore));
    }

    /**
     * Grunt work method that performs all bootstrap http requests and returns
     * the server's http response.
     *
     * Assumes per the RFC that all requests are going to be GET.
     *
     * @param bootStrapURI The URI to call as a part of this request.
     * @return Promise of the server response with a JSON body.
     */
    private CompletableFuture<ResponseEntity<JsonNode>>
        makeBootstrapRequest(URI bootStrapURI)
    {
        HttpEntity<?> entity = new HttpEntity<>(requestHeaders);
        CompletableFuture<ResponseEntity<JsonNode>> future =
            new CompletableFuture<ResponseEntity<JsonNode>>();

        try
        {
            ResponseEntity<JsonNode> rVal =
                restClient.exchange(bootStrapURI, HttpMethod.GET,
                                    entity, JsonNode.class);
            future.complete(rVal);
        }
        catch(RestClientException ex)
        {
            future.completeExceptionally(ex);
        }

        return future;
    }

    /**
     * Parses the results from any bootstrap request calling the provided
     * mapping
     */
    private void parseBootstrapResults(JsonNode bootstrapData,
                                       RDAPAuthorityStore authorityStore,
                                       ResourceMapper mapper)
    {
        JsonNode version = bootstrapData.get("version");
        if(version == null || SUPPORTED_VERSIONS.contains(version.asText()) == false)
        {
            throw new BootstrapVersionException(
                version == null ? "null" : version.asText(), SUPPORTED_VERSIONS);
        }

        ObjectMapper oMapper = new ObjectMapper();
        BootstrapResult result = null;

        try
        {
            result = oMapper.treeToValue(bootstrapData, BootstrapResult.class);
        }
        catch(JsonProcessingException ex)
        {
            throw new RuntimeException(ex);
        }

        for(BootstrapService service : result.getServices())
        {
            List<URI> serviceURIs = null;

            try
            {
                serviceURIs = service.getServersByURI();
            }
            catch(URISyntaxException ex)
            {
                throw new RuntimeException(ex);
            }

            RDAPAuthority authority =
                authorityStore.findAuthorityByURI(serviceURIs);

            if(authority == null)
            {
                authority = authorityStore.createAnonymousAuthority();
                authority.addServers(serviceURIs);
            }

            try
            {
                mapper.process(authority, service);
            }
            catch(Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Init function responsible for configuring the URI's used for fetching
     * IANA bootstrap data.
     *
     * @param baseURI Base URI to use for all specific endpoints created
     */
    private void setupURIs(String baseURI)
    {
        asnURI = URI.create(baseURI + "asn.json");
        domainURI = URI.create(baseURI + "dns.json");
        ipv4URI = URI.create(baseURI + "ipv4.json");
        ipv6URI = URI.create(baseURI + "ipv6.json");
    }

    /**
     * Drives the main update cycle for asn bootstrap results.
     *
     * @return Promise that's complete when an IANA asn update has
     * completed.
     */
    private CompletableFuture<Void> updateASNData(ResourceStore store,
                                                  RDAPAuthorityStore authorityStore)
    {
        return makeBootstrapRequest(asnURI)
            .thenAccept((ResponseEntity<JsonNode> entity) ->
            {
                parseBootstrapResults(entity.getBody(), authorityStore,
                    (RDAPAuthority authority, BootstrapService service) ->
                    {
                        for(String strAsnRange : service.getResources())
                        {
                            AsnRange asnRange = AsnRange.parse(strAsnRange);
                            store.putAutnumMapping(asnRange, authority);
                        }
                    });
            });
    }

    /**
     * Drives the main update cycle for domain bootstrap results
     *
     * @return Promise that's complete when an IANA domain update has
     * completed.
     */
    private CompletableFuture<Void> updateDomainData(ResourceStore store,
                                                     RDAPAuthorityStore authorityStore)
    {
        return makeBootstrapRequest(domainURI)
            .thenAccept((ResponseEntity<JsonNode> entity) ->
            {
                parseBootstrapResults(entity.getBody(), authorityStore,
                    (RDAPAuthority authority, BootstrapService service) ->
                    {
                        for(String tldDomain : service.getResources())
                        {
                            Domain domain = new Domain(tldDomain);
                            store.putDomainMapping(domain, authority);
                        }
                    });
            });
    }

    /**
     * Utility method that shares the same logic for driving all ip address
     * updates.
     *
     * @param ipBootstrapURI The URI for the ip bootstrap data to process
     * @return Promise that's complete when an IANA ip update has
     * completed
     */
    private CompletableFuture<Void> updateIPAllData(URI ipBootstrapURI,
                                                    ResourceStore store,
                                                    RDAPAuthorityStore authorityStore)
    {
        return makeBootstrapRequest(ipBootstrapURI)
            .thenAccept((ResponseEntity<JsonNode> entity) ->
            {
                parseBootstrapResults(entity.getBody(), authorityStore,
                    (RDAPAuthority authority, BootstrapService service) ->
                    {
                        for(String strIpRange : service.getResources())
                        {
                            IpRange ipRange = IpRange.parse(strIpRange);
                            store.putIPMapping(ipRange, authority);
                        }
                    });
            });
    }

    /**
     * Drives the main update cycle for ipv4 bootstrap results.
     *
     * Proxies through to updateIPAllData()
     * @return Promise that's complete when an IANA ipv4 update has
     * complete.
     */
    private CompletableFuture<Void> updateIPv4Data(ResourceStore store,
                                                   RDAPAuthorityStore authorityStore)
    {
        return updateIPAllData(ipv4URI, store, authorityStore);
    }

    /**
     * Drives the main update cycle for ipv6 bootstrap results.
     *
     * Proxies through to updateIPAllData()
     * @return Promise that's complete when an IANA ipv6 update has
     * complete.
     */
    private CompletableFuture<Void> updateIPv6Data(ResourceStore store,
                                                   RDAPAuthorityStore authorityStore)
    {
        return updateIPAllData(ipv6URI, store, authorityStore);
    }
}
