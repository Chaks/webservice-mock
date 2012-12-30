/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jveda.mock.ws;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.VisitorSupport;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultElement;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.log.Log;

/**
 *
 * @author Darimireddi Chakravarthi
 */
public class MockServer {

  private static String soapResDir;
  private static String jsonConfigFile;
  private static Map<String, Long> hashLastModifiedMap = new ConcurrentHashMap<String, Long>();
  private static Map<String, String> soapResMap = new ConcurrentHashMap<String, String>();
  private static Map<String, Service> serviceMap = new ConcurrentHashMap<String, Service>();

  /**
   *
   * @param args
   * @throws Exception
   */
  public static void main(String args[]) throws Exception {

    //Graffiti
    BufferedReader br =
            new BufferedReader(new InputStreamReader(MockServer.class.getResourceAsStream("/graffiti")));

    String line;
    while ((line = br.readLine()) != null) {
      System.out.println(line);
    }

    br.close();

    // 6789   /services/HelloWorld    /home/dchakr/testDir    services.json
    soapResDir = args[2];
    jsonConfigFile = args[3];

    MockServer webServiceMock = new MockServer();
    webServiceMock.loadServiceConfig();

    Log.info("Service Map: " + serviceMap);

    //isDynamicReloadingEnabled = "true".equals(args[3]) ? true : false;

    Server server = webServiceMock.createServer(args[0], args[1], args[2]);
    Log.info("Creating server with context " + args[1] + " on port " + args[0] + ", serving " + args[2]);

    webServiceMock.startServer(server);
  }

  /**
   *
   * @param port
   * @param context
   * @param testDir
   * @return
   */
  public Server createServer(String port, String context, String testDir) {
    //Build Server
    Server server = new Server(Integer.parseInt(port));
    HandlerList handlers = new HandlerList();

    //Setup context path
    ContextHandler contextHandler = new ContextHandler();
    contextHandler.setContextPath(context);
    //context.setResourceBase(".");
    //context.setClassLoader(Thread.currentThread().getContextClassLoader());
    //server.setHandler(contextHandler);

    //Setup resource handler
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirectoriesListed(true);
    resourceHandler.setResourceBase(testDir);

    handlers.setHandlers(new Handler[]{contextHandler, resourceHandler, getMockHandler()});

    //Setup Handler
    server.setHandler(handlers);

    return server;
  }

  /**
   *
   * @return
   */
  public Handler getMockHandler() {
    Handler handler = new AbstractHandler() {
      public void handle(String contextPath, Request request, HttpServletRequest httpServletRequest,
              HttpServletResponse httpServletResponse) throws IOException, ServletException {
        Log.info("Context: " + contextPath);

        //Load Json config again
        loadServiceConfig();
        Service serviceObject = serviceMap.get(contextPath);
        if (null == serviceObject) {
          Log.info("Json config not available for the context " + contextPath);
          return;
        }

        Request baseRequest =
                httpServletRequest instanceof Request ? (Request) httpServletRequest : HttpConnection.getCurrentConnection().getRequest();

        //String soapRequest = IOUtils.toString(baseRequest.getInputStream()).replaceAll("\n", "");
        String soapRequest = IOUtils.toString(baseRequest.getInputStream());
        String soapRequestForHash = "";
        try {
          if (serviceObject.isDetachHeader()) {
            soapRequestForHash = detachHeaderFromSoapRequest(soapRequest);
          } else {
            soapRequestForHash = soapRequest;
          }

          if (serviceObject.getDetachElementList() != null && !serviceObject.getDetachElementList().isEmpty()) {
            soapRequestForHash =
                    detachElementsFromBody(soapRequestForHash, serviceObject.getDetachElementList());
          }
        } catch (Exception ex) {
          Logger.getLogger(MockServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        String soapRequestMD5Hash = createHash(soapRequestForHash);

        Log.info("SOAP request: " + soapRequest);
        //Log.info("SOAP request for creating hash, after detaching and dropping namespaces: " + soapRequestForHash);
        Log.info("SOAP request MD5 hash, after detaching and dropping namespaces: " + soapRequestMD5Hash);

        boolean isRefreshRequired = false;
        if (hashLastModifiedMap.containsKey(soapRequestMD5Hash)) {
          isRefreshRequired = refreshRequired(hashLastModifiedMap.get(soapRequestMD5Hash), serviceObject.getRefreshInterval());
        }

        String soapResponse = "";
        try {
          if (!hashFileExists(contextPath, soapRequestMD5Hash)) {
            Log.info("Hash file " + soapRequestMD5Hash + " not available");
            if (serviceObject.isMirrorEnabled()) {
              Log.info("Mirror enabled");
              Log.info("Hitting actual endpoint at "
                      + serviceObject.getHostName() + ":" + serviceObject.getPort() + "" + serviceObject.getContextPath());
              Socket socket = createSocket(serviceObject.getHostName(), serviceObject.getPort() + "");
              sendSoapRequest(socket, serviceObject.getContextPath(), soapRequest);
              soapResponse = receiveSoapResponse(socket);
              Log.info("Writing SOAP request to hash file " + soapRequestMD5Hash + "~");
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash + "~", soapRequestForHash);
              Log.info("Writing SOAP response to hash file " + soapRequestMD5Hash);
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash, soapResponse);
            } else {
              Log.info("Mirror not enabled");
              Log.info("Creating hash file " + soapRequestMD5Hash + " for the first time");
              createHashFile(contextPath, soapRequestMD5Hash);
            }
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          } else if (soapResMap.containsKey(soapRequestMD5Hash) && !isRefreshRequired) {
            //Detect file change and load the content
            if (hashLastModifiedMap.get(soapRequestMD5Hash) != getHashFileLastModified(contextPath, soapRequestMD5Hash)) {
              Log.info("Hash file " + soapRequestMD5Hash + " changed");
              Log.info("Refresh required for hash " + soapRequestMD5Hash);
              Log.info("Getting SOAP response from hash file " + soapRequestMD5Hash);
              soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
              soapResMap.put(soapRequestMD5Hash, soapResponse);
              hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
            } else {
              Log.info("Hash file " + soapRequestMD5Hash + " not changed");
              Log.info("Refresh not required for hash " + soapRequestMD5Hash);
              Log.info("SOAP response for the hash " + soapRequestMD5Hash + " available in In-Memory");
              soapResponse = soapResMap.get(soapRequestMD5Hash);
            }
          } else if (hashLastModifiedMap.containsKey(soapRequestMD5Hash) && isRefreshRequired) {
            Log.info("Refresh required for hash " + soapRequestMD5Hash);
            if (serviceObject.isMirrorEnabled()) {
              Log.info("Mirror enabled");
              Log.info("Hitting actual endpoint at "
                      + serviceObject.getHostName() + ":" + serviceObject.getPort() + "" + serviceObject.getContextPath());
              Socket socket = createSocket(serviceObject.getHostName(), serviceObject.getPort() + "");
              sendSoapRequest(socket, serviceObject.getContextPath(), soapRequest);
              soapResponse = receiveSoapResponse(socket);
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash, soapResponse); //Update content to hash file
            } else {
              Log.info("Mirror not  enabled");
              Log.info("Getting SOAP response from hash file " + soapRequestMD5Hash);
              soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
            }
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          } else if (hashFileExists(contextPath, soapRequestMD5Hash)) {
            Log.info("Getting SOAP response from already existing hash file " + soapRequestMD5Hash);
            soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          }
        } catch (Exception ex) {
          Logger.getLogger(MockServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        Log.info("SOAP response: " + soapResponse);

        httpServletResponse.setContentType("text/xml;charset=utf-8");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        httpServletResponse.getWriter().println(soapResponse);
        ((Request) httpServletRequest).setHandled(true);
      }
    };
    return handler;
  }

  /**
   *
   * @param server
   * @throws Exception
   */
  public void startServer(Server server) throws Exception {
    server.start();
    server.join();
  }

  private void loadServiceConfig() throws IOException {
    //JSON
    ObjectMapper mapper = new ObjectMapper();
    //JsonParser jsonParser = mapper.getJsonFactory().createJsonParser(new File(args[3]));
    List<Service> serviceList = mapper.readValue(new File(jsonConfigFile), new TypeReference<List<Service>>() {
    });

    for (Service jsonService : serviceList) {
      Service service = new Service();
      service.setContextPath(jsonService.getContextPath());
      service.setDetachElementList(jsonService.getDetachElementList());
      service.setDetachHeader(jsonService.isDetachHeader());
      service.setDirectoryBrowsing(jsonService.isDirectoryBrowsing());
      service.setHostName(jsonService.getHostName());
      service.setMirrorEnabled(jsonService.isMirrorEnabled());
      service.setPort(jsonService.getPort());
      service.setRefreshInterval(jsonService.getRefreshInterval());

      serviceMap.put(jsonService.getContextPath(), service);
    }
  }

  private String createHash(String s) {
    return DigestUtils.md5Hex(s);
  }

  private void createHashFile(String contextPath, String fileName) throws Exception {
    new File(soapResDir + File.separator + contextPath + File.separator + fileName).createNewFile();
  }

  private boolean hashFileExists(String contextPath, String fileName) {
    boolean fileExists = new File(soapResDir + File.separator + contextPath + File.separator + fileName).exists();
    return fileExists;
  }

  private long getHashFileLastModified(String contextPath, String fileName) {
    long lastModified = new File(soapResDir + File.separator + contextPath + File.separator + fileName).lastModified();
    return lastModified;
  }

  private boolean refreshRequired(long lastModifed, long refreshInterval) {
    Log.info("Current time " + new Date());
    Log.info("File last modified at " + new Date(lastModifed));
    Log.info("File refresh interval " + refreshInterval / 1000 + " seconds");
    return ((System.currentTimeMillis() - lastModifed) >= refreshInterval);
  }

  private String getSoapResponseFromHashFile(String contextPath, String fileName) throws Exception {
    return IOUtils.toString(new FileInputStream(soapResDir + File.separator + contextPath + File.separator + fileName));
  }

  private void writeSoapMessageToHashFile(String contextPath, String fileName, String soapResponse) throws Exception {
    BufferedWriter bw =
            new BufferedWriter(new FileWriter(new File(soapResDir + File.separator + contextPath + File.separator + fileName)));
    bw.write(soapResponse);
    bw.close();
  }

  private Socket createSocket(String actualHost, String actualPort) throws Exception {
    InetAddress address = InetAddress.getByName(actualHost);
    Socket socket = new Socket(address, Integer.parseInt(actualPort));
    return socket;
  }

  private void sendSoapRequest(Socket socket, String contextPath, String soapRequest) throws Exception {
    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    bw.write("POST " + contextPath + " HTTP/1.0\r\n");
    bw.write("Host: " + socket.getInetAddress().getHostName() + "\r\n");
    bw.write("Content-Length: " + soapRequest.length() + "\r\n");
    bw.write("Content-Type: text/xml; charset=\"utf-8\"\r\n");
    bw.write("\r\n");
    bw.write(soapRequest);
    bw.flush();
    //bw.close();
  }

  private String receiveSoapResponse(Socket socket) throws Exception {
    StringBuilder soapResponse = new StringBuilder();

    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    boolean initRead = false;
    String line;
    while ((line = br.readLine()) != null) {
      if (line.indexOf("<?xml") >= 0
              || line.indexOf("<soapenv") >= 0) {
        initRead = true;
      }
      if (initRead) {
        soapResponse.append(line);
        soapResponse.append("\n");
      }
    }

    br.close();

    return soapResponse.toString();
  }

  private String detachHeaderFromSoapRequest(String soapRequest) throws Exception {
    SAXReader reader = new SAXReader();
    Document document = reader.read(new StringReader(soapRequest));

    List<Element> elements = document.getRootElement().elements();
    for (Element element : elements) {
      if ("Header".equals(element.getName())) {
        Log.info("Detaching SOAP Header for hashing");
        element.detach();
        break;
      }
    }

    return document.asXML();
  }

  private String detachElementsFromBody(String soapRequest, List<String> detachElementList) throws Exception {

    SAXReader reader = new SAXReader();
    Document document = reader.read(new StringReader(soapRequest));
    /**
     * Clean namespaces
     */
    document.accept(new NameSpaceCleaner());
    for (String detachElement : detachElementList) {
      if (detachElement != null && !"".equals(detachElement)) {
        Node nodeToBeDetached = document.selectSingleNode(detachElement);
        if (nodeToBeDetached != null) {
          Log.info("Detaching SOAP Element " + nodeToBeDetached.getName() + " for hashing");
          nodeToBeDetached.detach();
        }
      }
    }

    return document.asXML();
  }
}

class NameSpaceCleaner extends VisitorSupport {

  @Override
  public void visit(Document document) {
    ((DefaultElement) document.getRootElement()).setNamespace(Namespace.NO_NAMESPACE);
    document.getRootElement().additionalNamespaces().clear();
  }

  @Override
  public void visit(Namespace namespace) {
    namespace.detach();
  }

  @Override
  public void visit(Attribute node) {
    if (node.toString().contains("xmlns")
            || node.toString().contains("xsi:")) {
      node.detach();
    }
  }

  @Override
  public void visit(Element node) {
    if (node instanceof DefaultElement) {
      ((DefaultElement) node).setNamespace(Namespace.NO_NAMESPACE);
    }
  }
}