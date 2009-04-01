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
package org.sakaiproject.kernel.persistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.sakaiproject.kernel.api.persistence.DataSourceService;

import java.util.List;

import javax.transaction.TransactionManager;

/**
 * Contains a list of service objects to be exportes.
 */
public class ServiceExportProvider implements Provider<List<Object>> {

  private List<Object> exports;

  /**
   * 
   */
  @Inject
  public ServiceExportProvider(DataSourceService dataSourceService,
      ScopedEntityManager scopedEntityManager, TransactionManager transactionManager) {
    exports = Lists.immutableList(dataSourceService, scopedEntityManager, transactionManager);
  }

  /**
   * {@inheritDoc}
   * 
   * @see com.google.inject.Provider#get()
   */
  public List<Object> get() {
    return exports;
  }

}
