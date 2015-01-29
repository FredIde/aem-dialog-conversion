/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2014 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/

package com.adobe.cq.dialogconversion.impl.rules;

import com.adobe.cq.dialogconversion.AbstractDialogRewriteRule;
import com.adobe.cq.dialogconversion.DialogRewriteException;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Set;

import static com.adobe.cq.dialogconversion.DialogRewriteUtils.hasPrimaryType;
import static com.adobe.cq.dialogconversion.DialogRewriteUtils.hasXtype;

/**
 * Rule that rewrites the basic structure of dialogs. It creates a Granite UI container using either a "tabs" or
 * "fixedcolumns" layout. The components (tabs or widgets) of the original dialog are copied over and will be handled
 * by subsequent passes of the algorithm.
 */
@Component
@Service
@Properties({
    @Property(name="service.ranking", intValue = 1)
})
public class CqDialogRewriteRule extends AbstractDialogRewriteRule {

    private static final String PRIMARY_TYPE = "cq:Dialog";

    public boolean matches(Node root)
            throws RepositoryException {
        return hasPrimaryType(root, PRIMARY_TYPE);
    }

    public Node applyTo(Node root, Set<Node> finalNodes)
            throws DialogRewriteException, RepositoryException {
        // Granite UI dialog already exists at this location
        Node parent = root.getParent();
        if (parent.hasNode("cq:dialog")) {
            throw new DialogRewriteException("Could not rewrite dialog: cq:dialog node already exists");
        }

        boolean isTabbed = isTabbed(root);
        // get the items: in case of a tabbed dialog, these represent tabs, otherwise widgets
        Node dialogItems = getDialogItems(root);
        if (dialogItems == null) {
            throw new DialogRewriteException("Unable to find the dialog items");
        }

        // add cq:dialog node
        Node cqDialog = parent.addNode("cq:dialog", "nt:unstructured");
        finalNodes.add(cqDialog);
        cqDialog.setProperty("sling:resourceType", "cq/gui/components/authoring/dialog");
        if (root.hasProperty("helpPath")) {
            cqDialog.setProperty("helpPath", root.getProperty("helpPath").getValue());
        }
        if (root.hasProperty("title")) {
            cqDialog.setProperty("jcr:title", root.getProperty("title").getValue());
        }

        // add content node as a panel or tabpanel widget (will be rewritten by the corresponding rule)
        String nodeType = isTabbed ? "cq:TabPanel" : "cq:Panel";
        Node content = cqDialog.addNode("content", nodeType);

        // add items child node
        Node items = content.addNode("items", "cq:WidgetCollection");

        // copy items
        NodeIterator iterator = dialogItems.getNodes();
        while (iterator.hasNext()) {
            Node item = iterator.nextNode();
            JcrUtil.copy(item, items, item.getName());
        }

        // remove old root and return new root
        root.remove();
        return cqDialog;
    }

    /**
     * Returns true if this dialog contains tabs, false otherwise.
     */
    private boolean isTabbed(Node dialog)
            throws RepositoryException {
        if (isTabPanel(dialog)) {
            return true;
        }
        Node items = getChild(dialog, "items");
        if (isTabPanel(items)) {
            return true;
        }
        if (items != null && isTabPanel(getChild(items, "tabs"))) {
            return true;
        }
        return false;
    }

    /**
     * Returns the items that this dialog consists of. These might be components, or - in case of a tabbed
     * dialog - tabs.
     */
    private Node getDialogItems(Node dialog)
            throws RepositoryException {
        // find first sub node called "items" of type "cq:WidgetCollection"
        Node items = dialog;
        do {
            items = getChild(items, "items");
        } while (items != null && !"cq:WidgetCollection".equals(items.getPrimaryNodeType().getName()));
        if (items == null) {
            return null;
        }

        // check if there is a tab panel child called "tabs"
        Node tabs = getChild(items, "tabs");
        if (tabs != null && isTabPanel(tabs)) {
            return getChild(tabs, "items");
        }

        return items;
    }

    /**
     * Returns the child with the given name or null if it doesn't exist.
     */
    private Node getChild(Node node, String name)
            throws RepositoryException {
        if (node.hasNode(name)) {
            return node.getNode(name);
        }
        return null;
    }

    /**
     * Returns true if the specified node is a tab panel, false otherwise.
     */
    private boolean isTabPanel(Node node)
            throws RepositoryException {
        if (node == null) {
            return false;
        }
        if ("cq:TabPanel".equals(node.getPrimaryNodeType().getName())) {
            return true;
        }
        if (hasXtype(node, "tabpanel")) {
            return true;
        }
        return false;
    }

}
