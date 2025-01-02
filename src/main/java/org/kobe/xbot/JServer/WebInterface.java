package org.kobe.xbot.JServer;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.kobe.xbot.Utilities.ClientData;
import org.kobe.xbot.Utilities.ClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.SystemStatistics;
import org.kobe.xbot.Utilities.Utilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * WebInterface - A class for managing the XTABLES web user interface.
 * <p>
 * This class is responsible for starting and managing the web server that provides
 * a user interface for interacting with the XTABLES server. It serves static content
 * and exposes API endpoints for retrieving system and client statistics, as well as
 * rebooting the server.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: XTABLES
 */
public class WebInterface {
    private static final Gson gson = new Gson();
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private static final AtomicReference<WebInterface> instance = new AtomicReference<>();
    private Server userInterfaceServer;
    private final XTablesServer xTablesServer;

    /**
     * Constructs a new WebInterface instance.
     *
     * @param xTablesServer the associated XTablesServer instance
     */
    private WebInterface(XTablesServer xTablesServer) {
        this.xTablesServer = xTablesServer;
        start();
    }

    /**
     * Starts the web server.
     * <p>
     * Initializes and starts the Jetty server, configuring handlers for static content
     * and API endpoints.
     */
    private void start() {
        if (userInterfaceServer == null || !userInterfaceServer.isRunning()) {
            try {
                userInterfaceServer = new Server(new InetSocketAddress("0.0.0.0", 4880));
                ResourceHandler resourceHandler = new ResourceHandler();
                resourceHandler.setDirectoriesListed(true);
                URL resourceURL = WebInterface.class.getResource("/static");
                assert resourceURL != null;
                String resourceBase = resourceURL.toExternalForm();
                resourceHandler.setResourceBase(resourceBase);

                ContextHandler staticContext = new ContextHandler("/");
                staticContext.setHandler(resourceHandler);

                ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
                servletContextHandler.setContextPath("/");
                addServlets(xTablesServer, servletContextHandler);

                FilterHolder cors = servletContextHandler.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
                cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
                cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
                cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
                cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

                // Combine handlers
                HandlerList handlers = new HandlerList();
                handlers.addHandler(staticContext);
                handlers.addHandler(servletContextHandler);

                userInterfaceServer.setHandler(handlers);
                userInterfaceServer.start();
                logger.info("The local XTABLES user interface started at http://localhost:4880!");
            } catch (Exception e) {
                logger.warning("The local XTABLES user interface failed to start!");
            }
        }
    }

    /**
     * Initializes and returns a singleton instance of the WebInterface.
     *
     * @param xTablesServer the associated XTablesServer instance
     * @return the singleton WebInterface instance
     */
    public static WebInterface initialize(XTablesServer xTablesServer) {
        if (instance != null && instance.get() != null) {
            return instance.get();
        }
        if (instance != null) {
            instance.set(new WebInterface(xTablesServer));
            return instance.get();

        } else return null;
    }

    /**
     * Adds servlets for handling API requests to the given ServletContextHandler.
     *
     * @param server                the XTablesServer instance
     * @param servletContextHandler the ServletContextHandler to add servlets to
     */
    private void addServlets(XTablesServer server, ServletContextHandler servletContextHandler) {
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                SystemStatistics systemStatistics = new SystemStatistics(server);
                systemStatistics.setClientDataList(((List<XTableClientStatistics.ClientStatistics>) server.getClientRegistry().getClients().clone()).stream().map(m -> {
                    ClientData data = new ClientData(m.getIp(),
                            m.getHostname(),
                            m.getUuid());
                    data.setStats(gson.toJson(ClientStatistics.fromProtobuf(m)));
                    return data;
                }).collect(Collectors.toList()));
                try {
                    systemStatistics.setHostname(InetAddress.getLocalHost().getHostName());
                } catch (Exception ignored) {
                }

                resp.getWriter().println(gson.toJson(systemStatistics));
            }
        }), "/api/get");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String uuidParam = req.getParameter("uuid");

                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                if (uuidParam == null || uuidParam.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"No UUID found in parameter!\"}");
                    return;
                }
                UUID uuid;

                try {
                    uuid = UUID.fromString(uuidParam);
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Invalid UUID format!\"}");
                    return;
                }


                Optional<XTableClientStatistics.ClientStatistics> clientHandler = server.getClientRegistry().getClients().stream().filter(f -> f.getUuid().equals(uuid.toString())).findFirst();
                if (clientHandler.isPresent()) {
//                        ClientStatistics statistics = clientHandler.get().pingServerForInformationAndWait(3000);
//                        if (statistics != null) {
//                            resp.setStatus(HttpServletResponse.SC_OK);
//                            resp.getWriter().println(String.format("{ \"status\": \"success\", \"message\": %1$s}", gson.toJson(statistics)));
//                        } else {
//                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//                            resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"The client did not respond!\"}");
//                        }
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                }


            }
        }), "/api/ping");
        // Add a servlet to handle server reboot POST requests
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                try {
                    server.restart();
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().println("{ \"status\": \"success\", \"message\": \"Server has been rebooted!\"}");
                } catch (Exception e) {
                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Server cannot reboot while: " + server.getStatus().name() + "\"}");
                }

            }
        }), "/api/reboot");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                try {
                    if (!xTablesServer.getClientRegistry().shouldStopClientRegistry(5)) {
                        xTablesServer.getClientRegistrySession().set(ByteString.copyFrom(Utilities.generateRandomBytes(10)));
                        xTablesServer.getClientRegistry().getClients().clear();
                        xTablesServer.notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY)
                                .setValue(xTablesServer.getClientRegistrySessionId())
                                .build()
                        );
                    }
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().println("{ \"status\": \"success\", \"message\": \"Server has been reloaded!\"}");
                } catch (Exception e) {
                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Server cannot reboot while: " + server.getStatus().name() + "\"}");
                }

            }
        }), "/api/reloadRegistry");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                String data = server.getTable().toJSON();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(data);

            }
        }), "/api/data");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String uuidParam = req.getParameter("uuid");

                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                if (uuidParam == null || uuidParam.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"No UUID found in parameter!\"}");
                    return;
                }
                UUID uuid;

                try {
                    uuid = UUID.fromString(uuidParam);
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Invalid UUID format!\"}");
                    return;
                }


                Optional<XTableClientStatistics.ClientStatistics> clientHandler = ((List<XTableClientStatistics.ClientStatistics>) server.getClientRegistry().getClients().clone()).stream().filter(f -> f.getUuid().equals(uuid.toString())).findFirst();
                if (clientHandler.isPresent()) {
                    boolean success = server.getClientRegistry().getClients().remove(clientHandler.get());
                    if (success) {
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().println("{ \"status\": \"success\", \"message\": \"The client has been disconnected!\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                    }
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                }

            }
        }), "/api/disconnect");
    }
}
