// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;

public class DomainValidationBaseTest {

  protected static final String SECRET_NAME = "mysecret";
  protected static final String OVERRIDES_CM_NAME_IMAGE = "overrides-cm-image";
  protected static final String OVERRIDES_CM_NAME_MODEL = "overrides-cm-model";
  protected static final String VOLUME_MOUNT_PATH_1 = "$(MY_ENV)/bin";
  protected static final String ENV_NAME1 = "MY_ENV";
  protected static final String BAD_MY_ENV_VALUE = "projectdir";
  protected static final String GOOD_MY_ENV_VALUE = "/projectdir";
  protected static final String VOLUME_PATH_1 = "/usr";
  protected static final String END_VOLUME_MOUNT_PATH_1 = "/projectdir/bin";

  protected final KubernetesResourceLookupStub resourceLookup = new KubernetesResourceLookupStub();

  /**
   *  Types of Kubernetes resources which can be looked up on a domain.
   *
   */
  protected enum KubernetesResourceType {
    Secret, ConfigMap
  }

  @SuppressWarnings("SameParameterValue")
  protected static class KubernetesResourceLookupStub implements KubernetesResourceLookup {
    private final Map<KubernetesResourceType, List<V1ObjectMeta>> definedResources = new ConcurrentHashMap<>();

    List<V1ObjectMeta> getResourceList(KubernetesResourceType type) {
      return definedResources.computeIfAbsent(type, (key) -> new ArrayList<>());
    }

    void undefineResource(String name, KubernetesResourceType type, String namespace) {
      getResourceList(type).removeIf(v1ObjectMeta -> hasSpecification(v1ObjectMeta, name, namespace));
    }

    public void defineResource(String name, KubernetesResourceType type, String namespace) {
      Optional.ofNullable(getResourceList(type)).orElse(Collections.emptyList())
          .add(new V1ObjectMeta().name(name).namespace(namespace));
    }

    @Override
    public boolean isSecretExists(String name, String namespace) {
      return isResourceExists(name, KubernetesResourceType.Secret, namespace);
    }

    @Override
    public boolean isConfigMapExists(String name, String namespace) {
      return isResourceExists(name, KubernetesResourceType.ConfigMap, namespace);
    }

    private boolean isResourceExists(String name, KubernetesResourceType type, String namespace) {
      return getResourceList(type).stream().anyMatch(m -> hasSpecification(m, name, namespace));
    }

    boolean hasSpecification(V1ObjectMeta m, String name, String namespace) {
      return Objects.equals(name, m.getName()) && Objects.equals(namespace, m.getNamespace());
    }
  }

  public Domain readDomain(String resourceName) throws IOException {
    return readDomain(resourceName, false);
  }

  public Domain readDomain(String resourceName, boolean isFile) throws IOException {
    String json = jsonFromYaml(resourceName, isFile);
    return getGsonBuilder().fromJson(json, Domain.class);
  }

  private String jsonFromYaml(String resourceName, boolean isFile) throws IOException {
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj;
    if (isFile) {
      obj = yamlReader.readValue(new File(resourceName), Object.class);
    } else {
      obj = yamlReader.readValue(resourceName, Object.class);
    }

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }
}
