/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ambari.logsearch.query.converter;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.ambari.logsearch.common.LogSearchConstants;
import org.apache.ambari.logsearch.common.LogType;
import org.apache.ambari.logsearch.dao.SolrSchemaFieldDao;
import org.apache.ambari.logsearch.model.request.impl.CommonSearchRequest;
import org.apache.ambari.logsearch.util.SolrUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.Query;
import org.springframework.data.solr.core.query.SimpleFilterQuery;
import org.springframework.data.solr.core.query.SimpleStringCriteria;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.ambari.logsearch.solr.SolrConstants.ServiceLogConstants.LOG_MESSAGE;

public abstract class AbstractSearchRequestQueryConverter<REQUEST_TYPE extends CommonSearchRequest, QUERY_TYPE extends Query>
  extends AbstractConverterAware<REQUEST_TYPE, QUERY_TYPE> {

  @Inject
  @Named("serviceSolrFieldDao")
  private SolrSchemaFieldDao serviceSolrSchemaFieldDao;

  @Inject
  @Named("auditSolrFieldDao")
  private SolrSchemaFieldDao auditSolrSchemaFieldDao;

  @Override
  public QUERY_TYPE convert(REQUEST_TYPE request) {
    QUERY_TYPE query = createQuery();
    int page = StringUtils.isNumeric(request.getPage()) ? new Integer(request.getPage()) : 0;
    int pageSize = StringUtils.isNumeric(request.getPageSize()) ? new Integer(request.getPageSize()) : Integer.MAX_VALUE;
    PageRequest pageRequest = new PageRequest(page, pageSize, sort(request));
    query.setPageRequest(pageRequest);
    Criteria criteria = new SimpleStringCriteria("*:*");
    query.addCriteria(criteria);
    return extendSolrQuery(request, query);
  }

  public abstract QUERY_TYPE extendSolrQuery(REQUEST_TYPE request, QUERY_TYPE query);

  public abstract Sort sort(REQUEST_TYPE request);

  public abstract QUERY_TYPE createQuery();

  public abstract LogType getLogType();

  public List<String> splitValueAsList(String value, String separator) {
    return StringUtils.isNotEmpty(value) ? Splitter.on(separator).omitEmptyStrings().splitToList(value) : null;
  }

  public Query addEqualsFilterQuery(Query query, String field, String value) {
    return this.addEqualsFilterQuery(query, field, value, false);
  }

  public Query addEqualsFilterQuery(Query query, String field, String value, boolean negate) {
    if (StringUtils.isNotEmpty(value)) {
      addFilterQuery(query, new Criteria(field).is(value), negate);
    }
    return query;
  }

  public Query addContainsFilterQuery(Query query, String field, String value) {
    return this.addContainsFilterQuery(query, field, value, false);
  }

  public Query addContainsFilterQuery(Query query, String field, String value, boolean negate) {
    if (StringUtils.isNotEmpty(value)) {
      addFilterQuery(query, new Criteria(field).contains(value), negate);
    }
    return query;
  }

  public Query addInFilterQuery(Query query, String field, List<String> values) {
    return this.addInFilterQuery(query, field, values, false);
  }

  public Query addInFilterQuery(Query query, String field, List<String> values, boolean negate) {
    if (CollectionUtils.isNotEmpty(values)) {
      addFilterQuery(query, new Criteria(field).is(values), negate);
    }
    return query;
  }

  public Query addRangeFilter(Query query, String field, String from, String to) {
    return this.addRangeFilter(query, field, from, to, false);
  }

  public Query addRangeFilter(Query query, String field, String from, String to, boolean negate) { // TODO use criteria.between without escaping
    String fromValue = StringUtils.isNotEmpty(from) ? from : "*";
    String toValue = StringUtils.isNotEmpty(to) ? to : "*";
    addFilterQuery(query, new SimpleStringCriteria(field + ":[" + fromValue +" TO "+ toValue + "]" ), negate);
    return query;
  }

  public Query addIncludeFieldValues(Query query, String fieldValuesMapStr) {
    if (StringUtils.isNotEmpty(fieldValuesMapStr)) {
      List<Map<String, String>> criterias = new Gson().fromJson(fieldValuesMapStr,
        new TypeToken<List<HashMap<String, String>>>(){}.getType());
      for (Map<String, String> criteriaMap : criterias) {
        for (Map.Entry<String, String> fieldEntry : criteriaMap.entrySet()) {
          escapeFieldValueByType(fieldEntry);
          addFilterQuery(query, new Criteria(fieldEntry.getKey()).is(escapeFieldValueByType(fieldEntry)), false);
        }
      }
    }
    return query;
  }

  public Query addExcludeFieldValues(Query query, String fieldValuesMapStr) {
    if (StringUtils.isNotEmpty(fieldValuesMapStr)) {
      List<Map<String, String>> criterias = new Gson().fromJson(fieldValuesMapStr,
        new TypeToken<List<HashMap<String, String>>>(){}.getType());
      for (Map<String, String> criteriaMap : criterias) {
        for (Map.Entry<String, String> fieldEntry : criteriaMap.entrySet()) {
          addFilterQuery(query, new Criteria(fieldEntry.getKey()).is(escapeFieldValueByType(fieldEntry)), true);
        }
      }
    }
    return query;
  }

  private String escapeFieldValueByType(Map.Entry<String, String> fieldEntry) {
    String escapedFieldValue;
    if (fieldEntry.getKey().equalsIgnoreCase(LOG_MESSAGE)) {
      escapedFieldValue = SolrUtil.escapeForLogMessage(fieldEntry.getValue());
    } else {
      escapedFieldValue = SolrUtil.putWildCardByType(fieldEntry.getValue(), fieldEntry.getKey(), getSchemaFieldsTypeMapByLogType(getLogType()));
    }
    return escapedFieldValue;
  }

  private void addFilterQuery(Query query, Criteria criteria, boolean negate) {
    if (negate) {
      criteria.not();
    }
    query.addFilterQuery(new SimpleFilterQuery(criteria));
  }

  private Map<String, String> getSchemaFieldsTypeMapByLogType(LogType logType) {
    return LogType.AUDIT.equals(logType) ? auditSolrSchemaFieldDao.getSchemaFieldTypeMap() : serviceSolrSchemaFieldDao.getSchemaFieldTypeMap();
  }

}
