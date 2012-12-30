/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jveda.mock.ws;

import java.util.List;

/**
 *
 * @author Darimireddi Chakravarthi
 */
public class Service {

  private String hostName;
  private String contextPath;
  private List<String> detachElementList;
  private int port;
  private long refreshInterval;
  private boolean mirrorEnabled;
  private boolean detachHeader;
  private boolean directoryBrowsing;

  /**
   *
   * @return
   */
  public String getHostName() {
    return hostName;
  }

  /**
   *
   * @param hostName
   */
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  /**
   *
   * @return
   */
  public String getContextPath() {
    return contextPath;
  }

  /**
   *
   * @param contextPath
   */
  public void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  /**
   *
   * @return
   */
  public List<String> getDetachElementList() {
    return detachElementList;
  }

  /**
   *
   * @param detachElementList
   */
  public void setDetachElementList(List<String> detachElementList) {
    this.detachElementList = detachElementList;
  }

  /**
   *
   * @return
   */
  public int getPort() {
    return port;
  }

  /**
   *
   * @param port
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   *
   * @return
   */
  public long getRefreshInterval() {
    return refreshInterval;
  }

  /**
   *
   * @param refreshInterval
   */
  public void setRefreshInterval(long refreshInterval) {
    this.refreshInterval = refreshInterval;
  }

  /**
   *
   * @return
   */
  public boolean isMirrorEnabled() {
    return mirrorEnabled;
  }

  /**
   *
   * @param mirrorEnabled
   */
  public void setMirrorEnabled(boolean mirrorEnabled) {
    this.mirrorEnabled = mirrorEnabled;
  }

  /**
   *
   * @return
   */
  public boolean isDetachHeader() {
    return detachHeader;
  }

  /**
   *
   * @param detachHeader
   */
  public void setDetachHeader(boolean detachHeader) {
    this.detachHeader = detachHeader;
  }

  /**
   *
   * @return
   */
  public boolean isDirectoryBrowsing() {
    return directoryBrowsing;
  }

  /**
   *
   * @param directoryBrowsing
   */
  public void setDirectoryBrowsing(boolean directoryBrowsing) {
    this.directoryBrowsing = directoryBrowsing;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Service [");
    builder.append("contextPath=").append(contextPath);
    builder.append(", detachElementList=").append(detachElementList);
    builder.append(", detachHeader=").append(detachHeader);
    builder.append(", directoryBrowsing=").append(directoryBrowsing);
    builder.append(", hostName=").append(hostName);
    builder.append(", mirrorEnabled=").append(mirrorEnabled);
    builder.append(", port=").append(port);
    builder.append(", refreshInterval=").append(refreshInterval);
    builder.append("]");
    return builder.toString();
  }
}
