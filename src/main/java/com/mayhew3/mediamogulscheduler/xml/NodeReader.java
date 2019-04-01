package com.mayhew3.mediamogulscheduler.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface NodeReader {

  @NotNull
  Document readXMLFromUrl(String urlString) throws IOException, SAXException;

  @NotNull
  Document readXMLFromFile(String filePath) throws IOException, SAXException;

  @NotNull
  Node getNodeWithTag(NodeList nodeList, String tag) throws BadlyFormattedXMLException;

  @Nullable
  Node getNullableNodeWithTag(NodeList nodeList, String tag);

  @NotNull
  List<Node> getAllNodesWithTag(NodeList nodeList, String tag);

  @Nullable
  String getValueOfSimpleStringNullableNode(NodeList nodeList, String tag);

  @NotNull
  String getValueOfSimpleStringNode(NodeList nodeList, String tag) throws BadlyFormattedXMLException;

  @NotNull
  Document recoverDocument(InputStream inputStream) throws IOException, SAXException;
}
