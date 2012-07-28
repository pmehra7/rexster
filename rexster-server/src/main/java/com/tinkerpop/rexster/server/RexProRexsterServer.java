package com.tinkerpop.rexster.server;

import com.tinkerpop.rexster.RexsterApplication;
import com.tinkerpop.rexster.filter.AbstractSecurityFilter;
import com.tinkerpop.rexster.filter.DefaultSecurityFilter;
import com.tinkerpop.rexster.protocol.filter.RexProMessageFilter;
import com.tinkerpop.rexster.protocol.filter.ScriptFilter;
import com.tinkerpop.rexster.protocol.filter.SessionFilter;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class RexProRexsterServer implements RexsterServer {

    private static final Logger logger = Logger.getLogger(RexProRexsterServer.class);
    private final XMLConfiguration properties;
    private final Integer rexproServerPort;
    private final String rexproServerHost;
    private final TCPNIOTransport tcpTransport;

    public RexProRexsterServer(final XMLConfiguration properties) {
        this.properties = properties;
        this.rexproServerPort = properties.getInteger("rexpro-server-port", new Integer(8184));
        this.rexproServerHost = properties.getString("rexpro-server-host", "0.0.0.0");
        this.tcpTransport = TCPNIOTransportBuilder.newInstance().build();
    }

    @Override
    public void stop() throws Exception {
        this.tcpTransport.stop();
    }

    @Override
    public void start() throws Exception {
        final FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new RexProMessageFilter());

        final HierarchicalConfiguration securityConfiguration = properties.configurationAt("security.authentication");
        final String securityFilterType = securityConfiguration.getString("type");
        if (securityFilterType.equals("none")) {
            logger.info("Rexster configured with no security.");
        } else {
            final AbstractSecurityFilter filter;
            if (securityFilterType.equals("default")) {
                filter = new DefaultSecurityFilter();
                filterChainBuilder.add(filter);
            } else {
                filter = (AbstractSecurityFilter) Class.forName(securityFilterType).newInstance();
                filterChainBuilder.add(filter);
            }

            filter.configure(properties);

            logger.info("Rexster configured with [" + filter.getName() + "].");
        }

        final WebServerRexsterApplicationProvider provider = new WebServerRexsterApplicationProvider();
        final RexsterApplication application = provider.getValue();

        filterChainBuilder.add(new SessionFilter(application));
        filterChainBuilder.add(new ScriptFilter(application));

        this.tcpTransport.setIOStrategy(WorkerThreadIOStrategy.getInstance());
        this.tcpTransport.setProcessor(filterChainBuilder.build());
        this.tcpTransport.bind(rexproServerHost, rexproServerPort);

        this.tcpTransport.start();

        logger.info("RexPro serving on port: [" + rexproServerPort + "]");
    }
}
