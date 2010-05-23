/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.activemq;

import org.apache.activemq.broker.BrokerService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {
  private BrokerService broker;

  public void start(BundleContext arg0) throws Exception {
    String brokerUrl = System.getProperty("activemq.broker.url");
    if (brokerUrl == null) {
      String brokerProtocol = System.getProperty("activemq.broker.protocol", "tcp");
      String brokerHost = System.getProperty("activemq.broker.host", "localhost");
      String brokerPort = System.getProperty("activemq.broker.port", "61616");
      brokerUrl = brokerProtocol + "://" + brokerHost + ":" + brokerPort;
    }

    broker = new BrokerService();

    // configure the broker
    broker.addConnector(brokerUrl);

    broker.start();
  }

  public void stop(BundleContext arg0) throws Exception {
    if (broker != null) {
      broker.stop();
    }
  }

}
