/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jveda.mock.ws;

import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlImporter;
import com.eviware.soapui.model.iface.Operation;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Darimireddi Chakravarthi
 */
public class SoapGenerator {
  
  final static org.slf4j.Logger logger = LoggerFactory.getLogger(SoapGenerator.class);
  
  public static void main(String args[]) {
    String wsdlUrl = args[0];
    try {
      WsdlProject project = new WsdlProject();
      WsdlInterface[] wsdls = WsdlImporter.importWsdl(project, wsdlUrl);
      WsdlInterface wsdl = wsdls[0];
      for (Operation operation : wsdl.getOperationList()) {
        WsdlOperation wsdlOperation = (WsdlOperation) operation;
        logger.info("---------------------------------------------------------------------------------------------------------------------------------------------------");
        logger.info("Operation: " + wsdlOperation.getName());
        logger.info("^^^^^^^^^^^^^^^^^^^^^^^");
        logger.info("SOAP Request: " + wsdlOperation.createRequest(true));
        logger.info("^^^^^^^^^^^^^^^^^^^^^^^");
        logger.info("SOAP Response: " + wsdlOperation.createResponse(true));
        logger.info("---------------------------------------------------------------------------------------------------------------------------------------------------");
      }
    } catch (Exception ex) {
      logger.error("Exception while creating dummy response", ex);
    }    
  }
  
  public String createDummyResponse(Service serviceObj, String soapRequest) {
    String dummyResponse = "";
    try {
      WsdlProject project = new WsdlProject();
      WsdlInterface[] wsdls = WsdlImporter.importWsdl(project, getWsdlUrl(serviceObj));
      WsdlInterface wsdl = wsdls[0];
      for (Operation operation : wsdl.getOperationList()) {
        WsdlOperation wsdlOperation = (WsdlOperation) operation;
        if (wsdlOperation.getName().trim().length() > 0 && soapRequest.contains(wsdlOperation.getName())) {
          dummyResponse = wsdlOperation.createResponse(true);
        }
      }
    } catch (Exception ex) {
      logger.error("Exception while creating dummy response", ex);
    }
    return dummyResponse;
  }
  
  private String getWsdlUrl(Service serviceObj) {
    StringBuilder wsdlBuilder = new StringBuilder();
    wsdlBuilder.append("http://").append(serviceObj.getHostName()).append(":").append(serviceObj.getPort())
            .append(serviceObj.getContextPath()).append("?wsdl");
    
    return wsdlBuilder.toString();
  }
}