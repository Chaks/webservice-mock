/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jveda.mock.ws;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.StopWatch;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Darimireddi Chakravarthi
 */
public class MockServer {

  final static Logger logger = LoggerFactory.getLogger(MockServer.class);
  private static String soapResDir;
  private static String jsonConfigFile;
  private static final Map<String, Long> hashLastModifiedMap = new ConcurrentHashMap<String, Long>();
  private static final Map<String, String> soapResMap = new ConcurrentHashMap<String, String>();
  private static final Map<String, Service> serviceMap = new ConcurrentHashMap<String, Service>();

  /**
   *
   * @param args
   * @throws Exception
   */
  public static void main(String args[]) throws Exception {

    //Graffiti
    BufferedReader br = new BufferedReader(new InputStreamReader(MockServer.class.getResourceAsStream("/graffiti")));

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

    logger.info("Service Map: " + serviceMap);

    //isDynamicReloadingEnabled = "true".equals(args[3]) ? true : false;
    Server server = webServiceMock.createServer(args[0], args[1], args[2]);
    logger.info("Creating server with context " + args[1] + " on port " + args[0] + ", serving " + args[2]);

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
      @Override
      public void handle(String contextPath, Request request, HttpServletRequest httpServletRequest,
              HttpServletResponse httpServletResponse) throws IOException, ServletException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.info("Context: " + contextPath);

        //Load json config again
        //loadServiceConfig();
        Service serviceObject = serviceMap.get(contextPath);
        if (null == serviceObject) {
          logger.info("Json config not available for the context " + contextPath);
          return;
        }

        Request baseRequest
                = httpServletRequest instanceof Request ? (Request) httpServletRequest : HttpConnection.getCurrentConnection().getRequest();

        //String soapRequest = IOUtils.toString(baseRequest.getInputStream()).replaceAll("\n", "");
        String soapRequest = IOUtils.toString(baseRequest.getInputStream());

        String hashFileIdentifier = "";
        try {
          hashFileIdentifier = buildIdentifierForHash(soapRequest, serviceObject.getIdentifierList());
        } catch (Exception ex) {
          logger.error("Exception while building identifier for hash", ex);
        }

        String soapRequestForHash = "";
        try {
          if (serviceObject.isDetachHeader()) {
            soapRequestForHash = detachHeaderFromSoapRequest(soapRequest);
          } else {
            soapRequestForHash = soapRequest;
          }

          if (serviceObject.getDetachElementList() != null && !serviceObject.getDetachElementList().isEmpty()) {
            soapRequestForHash
                    = detachElementsFromBody(soapRequestForHash, serviceObject.getDetachElementList());
          }
        } catch (Exception ex) {
          logger.error("Exception while detaching", ex);
        }

        String soapRequestMD5Hash = createHash(soapRequestForHash, hashFileIdentifier);

        logger.debug("SOAP request: " + soapRequest);
        //logger.info("SOAP request for creating hash, after detaching and dropping namespaces: " + soapRequestForHash);
        logger.info("SOAP request MD5 hash, after detaching and dropping namespaces: " + soapRequestMD5Hash);

        boolean isRefreshRequired = false;
        if (hashLastModifiedMap.containsKey(soapRequestMD5Hash)) {
          isRefreshRequired = refreshRequired(hashLastModifiedMap.get(soapRequestMD5Hash), serviceObject.getRefreshInterval());
        }

        String soapResponse = "";
        try {
          /*
           * If refreshInterval is 0, hit the true endpoint.
           * Check for any operation exclusion, so that the response is always from a true endpoint.
           */
          if (serviceObject.getRefreshInterval() == 0
                  || isOperationExcluded(serviceObject.getIgnoreOperationList(), soapRequest)) {  //Mock disabled
            logger.info("Refresh interval is either '0' or operation is configured in the exclusion list");
            //logger.info("Mirror enabled");
            logger.info("Hitting actual endpoint at "
                    + serviceObject.getHostName() + ":" + serviceObject.getPort() + "" + serviceObject.getContextPath());
            Socket socket = createSocket(serviceObject.getHostName(), serviceObject.getPort() + "");
            sendSoapRequest(socket, serviceObject.getContextPath(), soapRequest);
            soapResponse = receiveSoapResponse(socket);
          } else if (!hashFileExists(contextPath, soapRequestMD5Hash)) {
            logger.info("Hash file " + soapRequestMD5Hash + " not available");
            if (serviceObject.isMirrorEnabled()) {
              logger.info("Mirror enabled");
              logger.info("Hitting actual endpoint at "
                      + serviceObject.getHostName() + ":" + serviceObject.getPort() + "" + serviceObject.getContextPath());
              Socket socket = createSocket(serviceObject.getHostName(), serviceObject.getPort() + "");
              sendSoapRequest(socket, serviceObject.getContextPath(), soapRequest);
              soapResponse = receiveSoapResponse(socket);
              logger.info("Writing SOAP request to hash file " + soapRequestMD5Hash + "~");
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash + "~", soapRequest);
              logger.info("Writing SOAP response to hash file " + soapRequestMD5Hash);
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash, soapResponse);
            } else {
              logger.info("Mirror not enabled");
              logger.info("Writing SOAP request to hash file " + soapRequestMD5Hash + "~");
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash + "~", soapRequest);
              logger.info("Writing dummy SOAP response to hash file " + soapRequestMD5Hash);
              SoapGenerator soapGenerator = new SoapGenerator();
              soapResponse = soapGenerator.createDummyResponse(serviceObject, soapRequest);
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash, soapResponse);
            }
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          } else if (soapResMap.containsKey(soapRequestMD5Hash) && !isRefreshRequired) {
            //Detect file change and load the content
            if (hashLastModifiedMap.get(soapRequestMD5Hash) != getHashFileLastModified(contextPath, soapRequestMD5Hash)) {
              logger.info("Hash file " + soapRequestMD5Hash + " changed");
              logger.info("Refresh required for hash " + soapRequestMD5Hash);
              logger.info("Getting SOAP response from hash file " + soapRequestMD5Hash);
              soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
              soapResMap.put(soapRequestMD5Hash, soapResponse);
              hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
            } else {
              logger.info("Hash file " + soapRequestMD5Hash + " not changed");
              logger.info("Refresh not required for hash " + soapRequestMD5Hash);
              logger.info("SOAP response for the hash " + soapRequestMD5Hash + " available in In-Memory");
              soapResponse = soapResMap.get(soapRequestMD5Hash);
            }
          } else if (hashLastModifiedMap.containsKey(soapRequestMD5Hash) && isRefreshRequired) {
            logger.info("Refresh required for hash " + soapRequestMD5Hash);
            if (serviceObject.isMirrorEnabled()) {
              logger.info("Mirror enabled");
              logger.info("Hitting actual endpoint at "
                      + serviceObject.getHostName() + ":" + serviceObject.getPort() + "" + serviceObject.getContextPath());
              Socket socket = createSocket(serviceObject.getHostName(), serviceObject.getPort() + "");
              sendSoapRequest(socket, serviceObject.getContextPath(), soapRequest);
              soapResponse = receiveSoapResponse(socket);
              writeSoapMessageToHashFile(contextPath, soapRequestMD5Hash, soapResponse); //Update content to hash file
            } else {
              logger.info("Mirror not  enabled");
              logger.info("Getting SOAP response from hash file " + soapRequestMD5Hash);
              soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
            }
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          } else if (hashFileExists(contextPath, soapRequestMD5Hash)) {
            logger.info("Getting SOAP response from already existing hash file " + soapRequestMD5Hash);
            soapResponse = getSoapResponseFromHashFile(contextPath, soapRequestMD5Hash);
            soapResMap.put(soapRequestMD5Hash, soapResponse);
            hashLastModifiedMap.put(soapRequestMD5Hash, getHashFileLastModified(contextPath, soapRequestMD5Hash));
          }
        } catch (Exception ex) {
          logger.error("Exception while processing", ex);
        }

        logger.debug("SOAP response: " + soapResponse);
        logger.info("SOAP response hash file " + soapResDir + serviceObject.getContextPath() + "/" + soapRequestMD5Hash);
        logger.info("");

        httpServletResponse.setContentType("text/xml;charset=utf-8");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        httpServletResponse.getWriter().println(soapResponse);
        ((Request) httpServletRequest).setHandled(true);
        
        stopWatch.stop();
        logger.debug(stopWatch.toString());
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
      service.setIdentifierList(jsonService.getIdentifierList());
      service.setIgnoreOperationList(jsonService.getIgnoreOperationList());
      service.setDetachHeader(jsonService.isDetachHeader());
      service.setDirectoryBrowsing(jsonService.isDirectoryBrowsing());
      service.setHostName(jsonService.getHostName());
      service.setMirrorEnabled(jsonService.isMirrorEnabled());
      service.setPort(jsonService.getPort());
      service.setRefreshInterval(jsonService.getRefreshInterval());

      serviceMap.put(jsonService.getContextPath(), service);
    }
  }

  private boolean isOperationExcluded(List<String> operationExclusionList, String soapRequest) {
    boolean operationExcluded = false;
    if (operationExclusionList != null) {
      for (String operation : operationExclusionList) {
        if (operation.trim().length() > 0 && soapRequest.contains(operation)) {
          operationExcluded = true;
          break;
        }
      }
    }
    return operationExcluded;
  }

  private String createHash(String soapRequest, String hashIdentifier) {
    StringBuilder hashFileName = new StringBuilder();
    hashFileName.append(hashIdentifier);
    hashFileName.append(DigestUtils.md5Hex(soapRequest));
    return hashFileName.toString();
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
    logger.info("Current time " + new Date());
    logger.info("File last modified at " + new Date(lastModifed));
    logger.info("File refresh interval " + refreshInterval + " minutes");
    return ((System.currentTimeMillis() - lastModifed) >= (refreshInterval * 60 * 1000));
  }

  private String getSoapResponseFromHashFile(String contextPath, String fileName) throws Exception {
    return IOUtils.toString(new FileInputStream(soapResDir + File.separator + contextPath + File.separator + fileName));
  }

  private void writeSoapMessageToHashFile(String contextPath, String fileName, String soapMessage) throws Exception {
    BufferedWriter bw
            = new BufferedWriter(new FileWriter(new File(soapResDir + File.separator + contextPath + File.separator + fileName)));
    bw.write(soapMessage);
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
      if (line.contains("<?xml") || line.contains("<soapenv")) {
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
        logger.info("Detaching SOAP Header for hashing");
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
          logger.info("Detaching SOAP Element " + nodeToBeDetached.getName() + " for hashing");
          nodeToBeDetached.detach();
        }
      }
    }

    return document.asXML();
  }

  private String buildIdentifierForHash(String soapRequest, List<String> identifierElementList) throws Exception {
    StringBuilder identifier = new StringBuilder();
    SAXReader reader = new SAXReader();
    Document document = reader.read(new StringReader(soapRequest));
    /**
     * Clean namespaces
     */
    document.accept(new org.jveda.mock.ws.NameSpaceCleaner());
    
    Element rootElement = document.getRootElement();
    for (Iterator i = rootElement.elementIterator("Body"); i.hasNext();) {
      Element element = (Element) i.next();
      for (Iterator j = element.elementIterator(); j.hasNext();) {
        Element operationElement = (Element) j.next();
        identifier.append(operationElement.getName());
        identifier.append("_");
        break;
      }
      break;
    }

    for (String identifierElement : identifierElementList) {
      if (identifierElement != null && !"".equals(identifierElement)) {
        Node identifiedNode = document.selectSingleNode(identifierElement);
        if (identifiedNode != null) {
          identifier.append(identifiedNode.getStringValue());
          identifier.append("_");
        }
      }
    }

    return identifier.toString();
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
