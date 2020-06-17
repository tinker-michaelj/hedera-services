/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.net.rlpx.discover;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.rlpx.discover.table.KademliaOptions;
import org.ethereum.net.rlpx.discover.table.NodeEntry;

public class DiscoverTask implements Runnable {
    private static final Logger logger = LogManager.getLogger("discover");

    NodeManager nodeManager;

    byte[] nodeId;

    public DiscoverTask(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
        nodeId = nodeManager.homeNode.getId();
    }

    @Override
    public void run() {
        discover(nodeId, 0, new ArrayList<Node>());
    }

    public synchronized void discover(byte[] nodeId, int round, List<Node> prevTried) {

        try {
//        if (!channel.isOpen() || round == KademliaOptions.MAX_STEPS) {
//            logger.info("{}", String.format("Nodes discovered %d ", table.getAllNodes().size()));
//            return;
//        }

            if (round == KademliaOptions.MAX_STEPS) {
                if(logger.isDebugEnabled()) {
                    logger.debug("Node table contains [{}] peers",
                        nodeManager.getTable().getNodesCount());
                    logger.debug("{}", String
                        .format("(KademliaOptions.MAX_STEPS) Terminating discover after %d rounds.",
                            round));
                    logger.trace("{}\n{}", String
                            .format("Nodes discovered %d ", nodeManager.getTable().getNodesCount()),
                        dumpNodes());
                }
                return;
            }

            List<Node> closest = nodeManager.getTable().getClosestNodes(nodeId);
            List<Node> tried = new ArrayList<>();

            for (Node n : closest) {
                if (!tried.contains(n) && !prevTried.contains(n)) {
                    try {
                        nodeManager.getNodeHandler(n).sendFindNode(nodeId);
                        tried.add(n);
                        Thread.sleep(50);
                    }catch (InterruptedException e) {
                    } catch (Exception ex) {
                        logger.error("Unexpected Exception " + ex, ex);
                    }
                }
                if (tried.size() == KademliaOptions.ALPHA) {
                    break;
                }
            }

//            channel.flush();

            if (tried.isEmpty()) {
                if(logger.isDebugEnabled()) {
                    logger.debug("{}", String
                        .format("(tried.isEmpty()) Terminating discover after %d rounds.", round));
                    logger.debug("{}\n{}", String
                            .format("Nodes discovered %d ", nodeManager.getTable().getNodesCount()),
                        dumpNodes());
                }
                return;
            }

            tried.addAll(prevTried);

            discover(nodeId, round + 1, tried);
        } catch (Exception ex) {
            logger.debug("{}", ex);
        }
    }

    private String dumpNodes() {
        String ret = "";
        for (NodeEntry entry : nodeManager.getTable().getAllNodes()) {
            ret += "    " + entry.getNode() + "\n";
        }
        return ret;
    }
}