package com.jihedailabs.devtools.sagalinter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A directed graph of one BPMN process's flow nodes, built from its sequenceFlow elements — a
 * boundary event is sourceRef for its own outgoing flows exactly like any task, so no special
 * casing is needed to make it a graph node. Matched by local name only (not namespace-qualified):
 * real-world files use varying prefixes (bpmn2:, bpmn:) for the same BPMN 2.0 spec elements.
 */
public class BpmnGraph {

    private final Map<String, String> displayNames = new HashMap<>();
    private final Map<String, List<String>> edges = new HashMap<>();
    private final Set<String> boundaryTimerIds = new HashSet<>();
    private final Set<String> gatewayIds = new HashSet<>();

    public static BpmnGraph parse(File bpmnFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(bpmnFile);

        BpmnGraph graph = new BpmnGraph();
        graph.index(document);
        return graph;
    }

    private void index(Document document) {
        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String localName = localName(element);
            String id = element.getAttribute("id");

            if (!id.isEmpty()) {
                String name = element.getAttribute("name");
                displayNames.put(id, name.isEmpty() ? id : name);
            }

            switch (localName) {
                case "sequenceFlow" -> {
                    String source = element.getAttribute("sourceRef");
                    String target = element.getAttribute("targetRef");
                    if (!source.isEmpty() && !target.isEmpty()) {
                        edges.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                    }
                }
                case "boundaryEvent" -> {
                    if (hasChildLocalName(element, "timerEventDefinition")) {
                        boundaryTimerIds.add(id);
                    }
                }
                case "exclusiveGateway", "inclusiveGateway" -> gatewayIds.add(id);
                default -> { /* not a node type this linter cares about */ }
            }
        }
    }

    private static boolean hasChildLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName(child).equals(localName)) {
                return true;
            }
        }
        return false;
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        return local != null ? local : node.getNodeName();
    }

    public Set<String> boundaryTimerIds() {
        return boundaryTimerIds;
    }

    public Set<String> gatewayIds() {
        return gatewayIds;
    }

    public String displayName(String id) {
        return displayNames.getOrDefault(id, id);
    }

    public List<String> outgoing(String nodeId) {
        return edges.getOrDefault(nodeId, List.of());
    }

    public Set<String> allNodeIds() {
        return displayNames.keySet();
    }

    /** True if this node's id or display name looks like it performs compensation/rollback —
     * this codebase (and most real sagas) model compensation as an ordinary task reached via
     * plain sequence flow, not the formal BPMN compensation-event mechanism, so matching on
     * naming convention is what's actually checkable without engine-level semantics. */
    public boolean looksLikeCompensation(String nodeId) {
        String haystack = (nodeId + " " + displayName(nodeId)).toLowerCase();
        return haystack.contains("compensat") || haystack.contains("rollback");
    }

    public boolean hasAnyCompensationNode() {
        return allNodeIds().stream().anyMatch(this::looksLikeCompensation);
    }

    /** Breadth-first reachability from {@code startId} to any node matching
     * {@link #looksLikeCompensation(String)}. */
    public boolean canReachCompensation(String startId) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>(List.of(startId));
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            if (!visited.add(current)) {
                continue;
            }
            if (looksLikeCompensation(current)) {
                return true;
            }
            queue.addAll(outgoing(current));
        }
        return false;
    }
}
