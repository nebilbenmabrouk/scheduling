/* 
* ################################################################
* 
* ProActive: The Java(TM) library for Parallel, Distributed, 
*            Concurrent computing with Security and Mobility
* 
* Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
* Contact: proactive-support@inria.fr
* 
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or any later version.
*  
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
* 
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
* USA
*  
*  Initial developer(s):               The ProActive Team
*                        http://www.inria.fr/oasis/ProActive/contacts.html
*  Contributor(s): 
* 
* ################################################################
*/ 
package org.objectweb.proactive.core.descriptor.data;


import org.objectweb.proactive.core.process.ExternalProcess;
/**
 * @author rquilici
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class VirtualMachineImpl implements VirtualMachine,java.io.Serializable
{
 //
  //  ----- PRIVATE MEMBERS -----------------------------------------------------------------------------------
  //

  /** the name of this VirtualMachine */
  private String name;

  /** true if this VirtualMachine is cyclic */
  private boolean cyclic;
  
  /** number of nodes that will be deployed on this VM. One node is the default */
  private String nodeNumber = "1";
  
  /** the acquisition method to use to find the VirtualMachine once created */
  private String acquisitionMethod;

  /** the process to start in order to create the JVM */
  private ExternalProcess process;

  //
  //  ----- CONSTRUCTORS -----------------------------------------------------------------------------------
  //

 /**
  * Contructs a new intance of VirtualNode
  */
  VirtualMachineImpl() {
  }


  //
  //  ----- PUBLIC METHODS -----------------------------------------------------------------------------------
  //
  
  public void setCyclic(boolean b) {
    cyclic = b;
  }
  
  public boolean getCyclic() {
    return cyclic;
  }
  
  public void setNodeNumber(String nodeNumber) throws java.io.IOException{
  	if (!cyclic) throw new java.io.IOException("non cyclic jvm cannot deploy more than one node");
  	this.nodeNumber = nodeNumber;
  }
  
  public String getNodeNumber(){
  	return this.nodeNumber;
  }


  public void setName(String s) {
    name = s;
  }
  
  public String getName() {
    return name;
  }
  
  
  public void setAcquisitionMethod(String s) {
    acquisitionMethod = s;
  }
  
  
  public String getAcquisitionMethod() {
    return acquisitionMethod;
  }
  
  
  public void setProcess(ExternalProcess p) {
    process = p;
  }
  
  
  public ExternalProcess getProcess() {
    return process;
  }
  
  /**
   * Returns the name of the machine where the process mapped to this virtual machine 
   * was launched.
   * @return String
   */
  public String getHostName(){
  	String hostName = process.getHostname();
  	if (hostName == null){
  		hostName = "localhost";
  	}
  	return hostName;
  }
}

