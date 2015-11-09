package org.btrplace.scheduler.ucc15.scale;

import org.btrplace.model.*;
import org.btrplace.model.constraint.Fence;
import org.btrplace.model.constraint.MinMTTR;
import org.btrplace.model.constraint.Offline;
import org.btrplace.model.constraint.SatConstraint;
import org.btrplace.model.view.ShareableResource;
import org.btrplace.model.view.network.Network;
import org.btrplace.model.view.network.Switch;
import org.btrplace.plan.ReconfigurationPlan;
import org.btrplace.scheduler.SchedulerException;
import org.btrplace.scheduler.choco.DefaultChocoScheduler;
import org.btrplace.scheduler.choco.DefaultParameters;
import org.btrplace.scheduler.choco.runner.SolutionStatistics;
import org.btrplace.scheduler.choco.runner.SolvingStatistics;
import org.chocosolver.solver.exception.ContradictionException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by vkherbac on 11/03/15.
 */
public class BtrPlace {

    String path = new File("").getAbsolutePath() +
            "/src/test/java/org/btrplace/scheduler/tests/ucc15/";

    public SolvingStatistics decommissioning_10gb() throws SchedulerException,ContradictionException {

        // Set nb of nodes and vms
        int nbNodesRack = 24;
        int nbSrcNodes = nbNodesRack * 2;
        int nbDstNodes = nbNodesRack * 1;
        int nbVMs = nbSrcNodes * 2;

        // Set mem + cpu for VMs and Nodes
        int memVM = 4, cpuVM = 1;
        int memSrcNode = 16, cpuSrcNode = 4;
        int memDstNode = 16, cpuDstNode = 4;

        // Set memoryUsed and dirtyRate (for all VMs)
        int tpl1MemUsed = 2000, tpl1MaxDirtySize = 5, tpl1MaxDirtyDuration = 3; double tpl1DirtyRate = 0; // idle vm
        int tpl2MemUsed = 4000, tpl2MaxDirtySize = 96, tpl2MaxDirtyDuration = 2; double tpl2DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl3MemUsed = 2000, tpl3MaxDirtySize = 96, tpl3MaxDirtyDuration = 2; double tpl3DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl4MemUsed = 4000, tpl4MaxDirtySize = 5, tpl4MaxDirtyDuration = 3; double tpl4DirtyRate = 0; // idle vm

        int powerBoot = 20;

        // New default model
        Model mo = new DefaultModel();
        Mapping ma = mo.getMapping();

        // Create online source nodes and offline destination nodes
        List<Node> srcNodes = new ArrayList<>(), dstNodes = new ArrayList<>();
        for (int i=0; i<nbSrcNodes; i++) { srcNodes.add(mo.newNode()); ma.addOnlineNode(srcNodes.get(i)); }
        for (int i=0; i<nbDstNodes; i++) { dstNodes.add(mo.newNode()); ma.addOfflineNode(dstNodes.get(i)); }

        // Set boot and shutdown time
        for (Node n : dstNodes) { mo.getAttributes().put(n, "boot", 120); /*~2 minutes to boot*/ }
        for (Node n : srcNodes) {  mo.getAttributes().put(n, "shutdown", 17); /*~30 seconds to shutdown*/ }

        // Create running VMs on src nodes
        List<VM> vms = new ArrayList<>(); VM v;
        for (int i=0; i<nbSrcNodes; i++) {
            if (i%2 == 0) {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl1MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl1DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl1MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl1MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl2MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl2DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl2MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl2MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
            else {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl3MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl3DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl3MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl3MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl4MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl4DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl4MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl4MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
        }

        // Add resource views
        ShareableResource rcMem = new ShareableResource("mem", 0, 0);
        ShareableResource rcCPU = new ShareableResource("cpu", 0, 0);
        for (Node n : srcNodes) { rcMem.setCapacity(n, memSrcNode); rcCPU.setCapacity(n, cpuSrcNode); }
        for (Node n : dstNodes) { rcMem.setCapacity(n, memDstNode); rcCPU.setCapacity(n, cpuDstNode); }
        for (VM vm : vms) { rcMem.setConsumption(vm, memVM); rcCPU.setConsumption(vm, cpuVM); }
        mo.attach(rcMem);
        mo.attach(rcCPU);

        // Add a NetworkView view
        Network net = new Network();
        Switch swSrcRack1 = net.newSwitch();
        Switch swSrcRack2 = net.newSwitch();
        Switch swDstRack1 = net.newSwitch();
        Switch swMain = net.newSwitch();
        net.connect(1000, swSrcRack1, srcNodes.subList(0,nbNodesRack));
        net.connect(1000, swSrcRack2, srcNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swDstRack1, dstNodes.subList(0,nbNodesRack));
        net.connect(10000, swMain, swSrcRack1, swSrcRack2, swDstRack1);
        mo.attach(net);
        net.generateDot(path + "topology.dot", false);

        // Set parameters
        DefaultParameters ps = new DefaultParameters();
        ps.setVerbosity(0);
        ps.setTimeLimit(10);
        //ps.setMaxEnd(600);
        ps.doOptimize(false);

        // Migrate all VMs to destination nodes
        List<SatConstraint> cstrs = new ArrayList<>();
        int vm_num = 0;
        for (int i=0; i<nbDstNodes; i++) {
            cstrs.add(new Fence(vms.get(vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(vm_num+1), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-1-vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-2-vm_num), Collections.singleton(dstNodes.get(i))));
            vm_num+=2;
        }

        // Shutdown source nodes
        for (Node n : srcNodes) { cstrs.add(new Offline(n)); }

        // Set a custom objective
        DefaultChocoScheduler sc = new DefaultChocoScheduler(ps);
        Instance i = new Instance(mo, cstrs, new MinMTTR());

        ReconfigurationPlan p;
        try {
            p = sc.solve(i);
            Assert.assertNotNull(p);
        } finally {
            return sc.getStatistics();
        }
    }

    public SolvingStatistics decommissioning_20gb() throws SchedulerException,ContradictionException {

        // Set nb of nodes and vms
        int nbNodesRack = 24;
        int nbSrcNodes = nbNodesRack * 4;
        int nbDstNodes = nbNodesRack * 2;
        int nbVMs = nbSrcNodes * 2;

        // Set mem + cpu for VMs and Nodes
        int memVM = 4, cpuVM = 1;
        int memSrcNode = 16, cpuSrcNode = 4;
        int memDstNode = 16, cpuDstNode = 4;

        // Set memoryUsed and dirtyRate (for all VMs)
        int tpl1MemUsed = 2000, tpl1MaxDirtySize = 5, tpl1MaxDirtyDuration = 3; double tpl1DirtyRate = 0; // idle vm
        int tpl2MemUsed = 4000, tpl2MaxDirtySize = 96, tpl2MaxDirtyDuration = 2; double tpl2DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl3MemUsed = 2000, tpl3MaxDirtySize = 96, tpl3MaxDirtyDuration = 2; double tpl3DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl4MemUsed = 4000, tpl4MaxDirtySize = 5, tpl4MaxDirtyDuration = 3; double tpl4DirtyRate = 0; // idle vm

        // New default model
        Model mo = new DefaultModel();
        Mapping ma = mo.getMapping();

        // Create online source nodes and offline destination nodes
        List<Node> srcNodes = new ArrayList<>(), dstNodes = new ArrayList<>();
        for (int i=0; i<nbSrcNodes; i++) { srcNodes.add(mo.newNode()); ma.addOnlineNode(srcNodes.get(i)); }
        for (int i=0; i<nbDstNodes; i++) { dstNodes.add(mo.newNode()); ma.addOfflineNode(dstNodes.get(i)); }

        // Set boot and shutdown time
        for (Node n : dstNodes) { mo.getAttributes().put(n, "boot", 120); /*~2 minutes to boot*/ }
        for (Node n : srcNodes) {  mo.getAttributes().put(n, "shutdown", 17); /*~30 seconds to shutdown*/ }

        // Create running VMs on src nodes
        List<VM> vms = new ArrayList<>(); VM v;
        for (int i=0; i<nbSrcNodes; i++) {
            if (i%2 == 0) {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl1MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl1DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl1MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl1MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl2MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl2DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl2MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl2MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
            else {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl3MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl3DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl3MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl3MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl4MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl4DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl4MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl4MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
        }

        // Add resource views
        ShareableResource rcMem = new ShareableResource("mem", 0, 0);
        ShareableResource rcCPU = new ShareableResource("cpu", 0, 0);
        for (Node n : srcNodes) { rcMem.setCapacity(n, memSrcNode); rcCPU.setCapacity(n, cpuSrcNode); }
        for (Node n : dstNodes) { rcMem.setCapacity(n, memDstNode); rcCPU.setCapacity(n, cpuDstNode); }
        for (VM vm : vms) { rcMem.setConsumption(vm, memVM); rcCPU.setConsumption(vm, cpuVM); }
        mo.attach(rcMem);
        mo.attach(rcCPU);

        // Add a NetworkView view
        Network net = new Network();
        Switch swSrcRack1 = net.newSwitch();
        Switch swSrcRack2 = net.newSwitch();
        Switch swSrcRack3 = net.newSwitch();
        Switch swSrcRack4 = net.newSwitch();
        Switch swDstRack1 = net.newSwitch();
        Switch swDstRack2 = net.newSwitch();
        Switch swMain = net.newSwitch();

        net.connect(1000, swSrcRack1, srcNodes.subList(0,nbNodesRack));
        net.connect(1000, swSrcRack2, srcNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swSrcRack3, srcNodes.subList(nbNodesRack*2,nbNodesRack*3));
        net.connect(1000, swSrcRack4, srcNodes.subList(nbNodesRack*3,nbNodesRack*4));
        net.connect(1000, swDstRack1, dstNodes.subList(0,nbNodesRack));
        net.connect(1000, swDstRack2, dstNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(20000, swMain, swSrcRack1, swSrcRack2, swSrcRack3, swSrcRack4, swDstRack1, swDstRack2);
        mo.attach(net);
        net.generateDot(path + "topology.dot", false);

        // Set parameters
        DefaultParameters ps = new DefaultParameters();
        ps.setVerbosity(0);
        ps.setTimeLimit(10);
        //ps.setMaxEnd(600);
        ps.doOptimize(false);

        // Migrate all VMs to destination nodes
        List<SatConstraint> cstrs = new ArrayList<>();
        int vm_num = 0;
        for (int i=0; i<nbDstNodes; i++) {
            cstrs.add(new Fence(vms.get(vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(vm_num+1), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-1-vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-2-vm_num), Collections.singleton(dstNodes.get(i))));
            vm_num+=2;
        }

        // Shutdown source nodes
        for (Node n : srcNodes) { cstrs.add(new Offline(n)); }

        // Set a custom objective
        DefaultChocoScheduler sc = new DefaultChocoScheduler(ps);
        Instance i = new Instance(mo, cstrs, new MinMTTR());

        ReconfigurationPlan p;
        try {
            p = sc.solve(i);
            Assert.assertNotNull(p);
        } finally {
            return sc.getStatistics();
        }
    }

    public SolvingStatistics decommissioning_40gb() throws SchedulerException,ContradictionException {

        // Set nb of nodes and vms
        int nbNodesRack = 24;
        int nbSrcNodes = nbNodesRack * 8;
        int nbDstNodes = nbNodesRack * 4;
        int nbVMs = nbSrcNodes * 2;

        // Set mem + cpu for VMs and Nodes
        int memVM = 4, cpuVM = 1;
        int memSrcNode = 16, cpuSrcNode = 4;
        int memDstNode = 16, cpuDstNode = 4;

        // Set memoryUsed and dirtyRate (for all VMs)
        int tpl1MemUsed = 2000, tpl1MaxDirtySize = 5, tpl1MaxDirtyDuration = 3; double tpl1DirtyRate = 0; // idle vm
        int tpl2MemUsed = 4000, tpl2MaxDirtySize = 96, tpl2MaxDirtyDuration = 2; double tpl2DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl3MemUsed = 2000, tpl3MaxDirtySize = 96, tpl3MaxDirtyDuration = 2; double tpl3DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl4MemUsed = 4000, tpl4MaxDirtySize = 5, tpl4MaxDirtyDuration = 3; double tpl4DirtyRate = 0; // idle vm

        // New default model
        Model mo = new DefaultModel();
        Mapping ma = mo.getMapping();

        // Create online source nodes and offline destination nodes
        List<Node> srcNodes = new ArrayList<>(), dstNodes = new ArrayList<>();
        for (int i=0; i<nbSrcNodes; i++) { srcNodes.add(mo.newNode()); ma.addOnlineNode(srcNodes.get(i)); }
        for (int i=0; i<nbDstNodes; i++) { dstNodes.add(mo.newNode()); ma.addOfflineNode(dstNodes.get(i)); }

        // Set boot and shutdown time
        for (Node n : dstNodes) { mo.getAttributes().put(n, "boot", 120); /*~2 minutes to boot*/ }
        for (Node n : srcNodes) {  mo.getAttributes().put(n, "shutdown", 17); /*~30 seconds to shutdown*/ }

        // Create running VMs on src nodes
        List<VM> vms = new ArrayList<>(); VM v;
        for (int i=0; i<nbSrcNodes; i++) {
            if (i%2 == 0) {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl1MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl1DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl1MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl1MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl2MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl2DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl2MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl2MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
            else {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl3MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl3DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl3MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl3MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl4MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl4DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl4MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl4MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
        }

        // Add resource views
        ShareableResource rcMem = new ShareableResource("mem", 0, 0);
        ShareableResource rcCPU = new ShareableResource("cpu", 0, 0);
        for (Node n : srcNodes) { rcMem.setCapacity(n, memSrcNode); rcCPU.setCapacity(n, cpuSrcNode); }
        for (Node n : dstNodes) { rcMem.setCapacity(n, memDstNode); rcCPU.setCapacity(n, cpuDstNode); }
        for (VM vm : vms) { rcMem.setConsumption(vm, memVM); rcCPU.setConsumption(vm, cpuVM); }
        mo.attach(rcMem);
        mo.attach(rcCPU);

        // Add a NetworkView view
        Network net = new Network();
        Switch swSrcRack1 = net.newSwitch();
        Switch swSrcRack2 = net.newSwitch();
        Switch swSrcRack3 = net.newSwitch();
        Switch swSrcRack4 = net.newSwitch();
        Switch swSrcRack5 = net.newSwitch();
        Switch swSrcRack6 = net.newSwitch();
        Switch swSrcRack7 = net.newSwitch();
        Switch swSrcRack8 = net.newSwitch();
        Switch swDstRack1 = net.newSwitch();
        Switch swDstRack2 = net.newSwitch();
        Switch swDstRack3 = net.newSwitch();
        Switch swDstRack4 = net.newSwitch();
        Switch swMain = net.newSwitch();

        net.connect(1000, swSrcRack1, srcNodes.subList(0,nbNodesRack));
        net.connect(1000, swSrcRack2, srcNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swSrcRack3, srcNodes.subList(nbNodesRack*2,nbNodesRack*3));
        net.connect(1000, swSrcRack4, srcNodes.subList(nbNodesRack*3,nbNodesRack*4));
        net.connect(1000, swSrcRack5, srcNodes.subList(nbNodesRack*4,nbNodesRack*5));
        net.connect(1000, swSrcRack6, srcNodes.subList(nbNodesRack*5,nbNodesRack*6));
        net.connect(1000, swSrcRack7, srcNodes.subList(nbNodesRack*6,nbNodesRack*7));
        net.connect(1000, swSrcRack8, srcNodes.subList(nbNodesRack*7,nbNodesRack*8));
        net.connect(1000, swDstRack1, dstNodes.subList(0,nbNodesRack));
        net.connect(1000, swDstRack2, dstNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swDstRack3, dstNodes.subList(nbNodesRack*2,nbNodesRack*3));
        net.connect(1000, swDstRack4, dstNodes.subList(nbNodesRack*3,nbNodesRack*4));
        net.connect(40000, swMain, swSrcRack1, swSrcRack2, swSrcRack3, swSrcRack4, swSrcRack5, swSrcRack6, swSrcRack7, swSrcRack8,
                swDstRack1, swDstRack2, swDstRack3, swDstRack4);
        mo.attach(net);
        net.generateDot(path + "topology.dot", false);

        // Set parameters
        DefaultParameters ps = new DefaultParameters();
        ps.setVerbosity(0);
        ps.setTimeLimit(60);
        //ps.setMaxEnd(600);
        ps.doOptimize(false);

        // Migrate all VMs to destination nodes
        List<SatConstraint> cstrs = new ArrayList<>();
        int vm_num = 0;
        for (int i=0; i<nbDstNodes; i++) {
            cstrs.add(new Fence(vms.get(vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(vm_num+1), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-1-vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-2-vm_num), Collections.singleton(dstNodes.get(i))));
            vm_num+=2;
        }

        // Shutdown source nodes
        for (Node n : srcNodes) { cstrs.add(new Offline(n)); }

        // Set a custom objective
        DefaultChocoScheduler sc = new DefaultChocoScheduler(ps);
        Instance i = new Instance(mo, cstrs, new MinMTTR());

        ReconfigurationPlan p;
        try {
            p = sc.solve(i);
            Assert.assertNotNull(p);
        } finally {
            return sc.getStatistics();
        }
    }

    public SolvingStatistics decommissioning_100gb() throws SchedulerException,ContradictionException {

        // Set nb of nodes and vms
        int nbNodesRack = 24;
        int nbSrcNodes = nbNodesRack * 20;
        int nbDstNodes = nbNodesRack * 10;
        int nbVMs = nbSrcNodes * 2;

        // Set mem + cpu for VMs and Nodes
        int memVM = 4, cpuVM = 1;
        int memSrcNode = 16, cpuSrcNode = 4;
        int memDstNode = 16, cpuDstNode = 4;

        // Set memoryUsed and dirtyRate (for all VMs)
        int tpl1MemUsed = 2000, tpl1MaxDirtySize = 5, tpl1MaxDirtyDuration = 3; double tpl1DirtyRate = 0; // idle vm
        int tpl2MemUsed = 4000, tpl2MaxDirtySize = 96, tpl2MaxDirtyDuration = 2; double tpl2DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl3MemUsed = 2000, tpl3MaxDirtySize = 96, tpl3MaxDirtyDuration = 2; double tpl3DirtyRate = 3; // stress --vm 1000 --bytes 70K
        int tpl4MemUsed = 4000, tpl4MaxDirtySize = 5, tpl4MaxDirtyDuration = 3; double tpl4DirtyRate = 0; // idle vm

        // New default model
        Model mo = new DefaultModel();
        Mapping ma = mo.getMapping();

        // Create online source nodes and offline destination nodes
        List<Node> srcNodes = new ArrayList<>(), dstNodes = new ArrayList<>();
        for (int i=0; i<nbSrcNodes; i++) { srcNodes.add(mo.newNode()); ma.addOnlineNode(srcNodes.get(i)); }
        for (int i=0; i<nbDstNodes; i++) { dstNodes.add(mo.newNode()); ma.addOfflineNode(dstNodes.get(i)); }

        // Set boot and shutdown time
        for (Node n : dstNodes) { mo.getAttributes().put(n, "boot", 120); /*~2 minutes to boot*/ }
        for (Node n : srcNodes) {  mo.getAttributes().put(n, "shutdown", 17); /*~30 seconds to shutdown*/ }

        // Create running VMs on src nodes
        List<VM> vms = new ArrayList<>(); VM v;
        for (int i=0; i<nbSrcNodes; i++) {
            if (i%2 == 0) {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl1MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl1DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl1MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl1MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl2MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl2DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl2MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl2MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
            else {
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl3MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl3DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl3MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl3MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
                v = mo.newVM(); vms.add(v);
                mo.getAttributes().put(v, "memUsed", tpl4MemUsed);
                mo.getAttributes().put(v, "dirtyRate", tpl4DirtyRate);
                mo.getAttributes().put(v, "maxDirtySize", tpl4MaxDirtySize);
                mo.getAttributes().put(v, "maxDirtyDuration", tpl4MaxDirtyDuration);
                ma.addRunningVM(v, srcNodes.get(i));
            }
        }

        // Add resource views
        ShareableResource rcMem = new ShareableResource("mem", 0, 0);
        ShareableResource rcCPU = new ShareableResource("cpu", 0, 0);
        for (Node n : srcNodes) { rcMem.setCapacity(n, memSrcNode); rcCPU.setCapacity(n, cpuSrcNode); }
        for (Node n : dstNodes) { rcMem.setCapacity(n, memDstNode); rcCPU.setCapacity(n, cpuDstNode); }
        for (VM vm : vms) { rcMem.setConsumption(vm, memVM); rcCPU.setConsumption(vm, cpuVM); }
        mo.attach(rcMem);
        mo.attach(rcCPU);

        // Add a NetworkView view
        Network net = new Network();
        Switch swSrcRack1 = net.newSwitch();
        Switch swSrcRack2 = net.newSwitch();
        Switch swSrcRack3 = net.newSwitch();
        Switch swSrcRack4 = net.newSwitch();
        Switch swSrcRack5 = net.newSwitch();
        Switch swSrcRack6 = net.newSwitch();
        Switch swSrcRack7 = net.newSwitch();
        Switch swSrcRack8 = net.newSwitch();
        Switch swSrcRack9 = net.newSwitch();
        Switch swSrcRack10 = net.newSwitch();
        Switch swSrcRack11 = net.newSwitch();
        Switch swSrcRack12 = net.newSwitch();
        Switch swSrcRack13 = net.newSwitch();
        Switch swSrcRack14 = net.newSwitch();
        Switch swSrcRack15 = net.newSwitch();
        Switch swSrcRack16 = net.newSwitch();
        Switch swSrcRack17 = net.newSwitch();
        Switch swSrcRack18 = net.newSwitch();
        Switch swSrcRack19 = net.newSwitch();
        Switch swSrcRack20 = net.newSwitch();
        Switch swDstRack1 = net.newSwitch();
        Switch swDstRack2 = net.newSwitch();
        Switch swDstRack3 = net.newSwitch();
        Switch swDstRack4 = net.newSwitch();
        Switch swDstRack5 = net.newSwitch();
        Switch swDstRack6 = net.newSwitch();
        Switch swDstRack7 = net.newSwitch();
        Switch swDstRack8 = net.newSwitch();
        Switch swDstRack9 = net.newSwitch();
        Switch swDstRack10 = net.newSwitch();
        Switch swMain = net.newSwitch();

        net.connect(1000, swSrcRack1, srcNodes.subList(0,nbNodesRack));
        net.connect(1000, swSrcRack2, srcNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swSrcRack3, srcNodes.subList(nbNodesRack*2,nbNodesRack*3));
        net.connect(1000, swSrcRack4, srcNodes.subList(nbNodesRack*3,nbNodesRack*4));
        net.connect(1000, swSrcRack5, srcNodes.subList(nbNodesRack*4,nbNodesRack*5));
        net.connect(1000, swSrcRack6, srcNodes.subList(nbNodesRack*5,nbNodesRack*6));
        net.connect(1000, swSrcRack7, srcNodes.subList(nbNodesRack*6,nbNodesRack*7));
        net.connect(1000, swSrcRack8, srcNodes.subList(nbNodesRack*7,nbNodesRack*8));
        net.connect(1000, swSrcRack9, srcNodes.subList(nbNodesRack*8,nbNodesRack*9));
        net.connect(1000, swSrcRack10, srcNodes.subList(nbNodesRack*9,nbNodesRack*10));
        net.connect(1000, swSrcRack11, srcNodes.subList(nbNodesRack*10,nbNodesRack*11));
        net.connect(1000, swSrcRack12, srcNodes.subList(nbNodesRack*11,nbNodesRack*12));
        net.connect(1000, swSrcRack13, srcNodes.subList(nbNodesRack*12,nbNodesRack*13));
        net.connect(1000, swSrcRack14, srcNodes.subList(nbNodesRack*13,nbNodesRack*14));
        net.connect(1000, swSrcRack15, srcNodes.subList(nbNodesRack*14,nbNodesRack*15));
        net.connect(1000, swSrcRack16, srcNodes.subList(nbNodesRack*15,nbNodesRack*16));
        net.connect(1000, swSrcRack17, srcNodes.subList(nbNodesRack*16,nbNodesRack*17));
        net.connect(1000, swSrcRack18, srcNodes.subList(nbNodesRack*17,nbNodesRack*18));
        net.connect(1000, swSrcRack19, srcNodes.subList(nbNodesRack*18,nbNodesRack*19));
        net.connect(1000, swSrcRack20, srcNodes.subList(nbNodesRack*19,nbNodesRack*20));
        net.connect(1000, swDstRack1, dstNodes.subList(0,nbNodesRack));
        net.connect(1000, swDstRack2, dstNodes.subList(nbNodesRack,nbNodesRack*2));
        net.connect(1000, swDstRack3, dstNodes.subList(nbNodesRack*2,nbNodesRack*3));
        net.connect(1000, swDstRack4, dstNodes.subList(nbNodesRack*3,nbNodesRack*4));
        net.connect(1000, swDstRack5, dstNodes.subList(nbNodesRack*4,nbNodesRack*5));
        net.connect(1000, swDstRack6, dstNodes.subList(nbNodesRack*5,nbNodesRack*6));
        net.connect(1000, swDstRack7, dstNodes.subList(nbNodesRack*6,nbNodesRack*7));
        net.connect(1000, swDstRack8, dstNodes.subList(nbNodesRack*7,nbNodesRack*8));
        net.connect(1000, swDstRack9, dstNodes.subList(nbNodesRack*8,nbNodesRack*9));
        net.connect(1000, swDstRack10, dstNodes.subList(nbNodesRack*9,nbNodesRack*10));
        net.connect(100000, swMain,
                swSrcRack1, swSrcRack2, swSrcRack3, swSrcRack4, swSrcRack5, swSrcRack6, swSrcRack7, swSrcRack8,
                swSrcRack9, swSrcRack10, swSrcRack11, swSrcRack12, swSrcRack13, swSrcRack14, swSrcRack15, swSrcRack16,
                swSrcRack17, swSrcRack8, swSrcRack19, swSrcRack20,
                swDstRack1, swDstRack2, swDstRack3, swDstRack4, swDstRack5, swDstRack6, swDstRack7, swDstRack8,
                swDstRack9, swDstRack10
        );
        mo.attach(net);
        net.generateDot(path + "topology.dot", false);

        // Set parameters
        DefaultParameters ps = new DefaultParameters();
        ps.setVerbosity(0);
        ps.setTimeLimit(60);
        //ps.setMaxEnd(600);
        ps.doOptimize(false);

        // Migrate all VMs to destination nodes
        List<SatConstraint> cstrs = new ArrayList<>();
        int vm_num = 0;
        for (int i=0; i<nbDstNodes; i++) {
            cstrs.add(new Fence(vms.get(vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(vm_num+1), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-1-vm_num), Collections.singleton(dstNodes.get(i))));
            cstrs.add(new Fence(vms.get(nbVMs-2-vm_num), Collections.singleton(dstNodes.get(i))));
            vm_num+=2;
        }

        // Shutdown source nodes
        for (Node n : srcNodes) { cstrs.add(new Offline(n)); }

        // Set a custom objective
        DefaultChocoScheduler sc = new DefaultChocoScheduler(ps);
        Instance i = new Instance(mo, cstrs, new MinMTTR());

        ReconfigurationPlan p;
        try {
            p = sc.solve(i);
            Assert.assertNotNull(p);
        } finally {
            return sc.getStatistics();
        }
    }

    @Test
    public void go() throws Exception {
        StringBuilder res = new StringBuilder("SIZE;DURATION\n");
        int nb = 10;
        for (int i = 0; i < nb; i++) {
            res.append("10;" + duration(decommissioning_10gb()) + ";BtrPlace\n");
        }
        for (int i = 0; i < nb; i++) {
            res.append("20;" + duration(decommissioning_20gb()) + ";BtrPlace\n");
        }
        for (int i = 0; i < nb; i++) {
            res.append("40;" + duration(decommissioning_40gb()) + ";BtrPlace\n");
        }
        for (int i = 0; i < nb; i++) {
            res.append("100;" + duration(decommissioning_100gb()) + ";BtrPlace\n");
        }
        System.err.println(res);
        Assert.fail();
    }

    public double duration(SolvingStatistics s) {
        SolutionStatistics x= s.getSolutions().get(0);
        System.out.println(s);
        return x.getTime() + s.getCoreRPBuildDuration() + s.getSpeRPDuration();
    }
}
