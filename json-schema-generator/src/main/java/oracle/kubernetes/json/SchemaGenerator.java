// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

public class SchemaGenerator {

  private static final String EXTERNAL_CLASS = "external";

  private static final List<Class<?>> PRIMITIVE_NUMBERS =
      Arrays.asList(byte.class, short.class, int.class, long.class, float.class, double.class);

  private static final String JSON_SCHEMA_REFERENCE = "http://json-schema.org/draft-04/schema#";

  private static final String TYPE = "type";
  private static final String ITEMS = "items";
  private static final String ARRAY = "array";
  private static final String STRING = "string";
  private static final String NUMBER = "number";
  private static final String BOOLEAN = "boolean";

  // A map of classes to their $ref values
  private final Map<Class<?>, String> references = new HashMap<>();

  // A map of found classes to their definitions or the constant EXTERNAL_CLASS.
  private final Map<Class<?>, Object> definedObjects = new HashMap<>();

  // a map of external class names to the external schema that defines them
  private final Map<String, String> schemaUrls = new HashMap<>();

  // if true generate the additionalProperties field to forbid fields not in the object's schema. Defaults to true.
  private boolean forbidAdditionalProperties = true;

  // if true, the object fields are implemented as references to definitions
  private boolean supportObjectReferences = true;

  // if true, generate the top-level schema version reference
  private boolean includeSchemaReference = true;

  // suppress descriptions for any contained packages
  private final Collection<String> suppressDescriptionForPackages = new ArrayList<>();
  private final Map<Class<?>, String> additionalPropertiesTypes = new HashMap<>();

  private final Collection<String> enabledFeatures = new ArrayList<>();

  /**
   * Returns a pretty-printed string corresponding to a generated schema.
   *
   * @param schema a schema generated by a call to #generate
   * @return a string version of the schema
   */
  public static String prettyPrint(Object schema) {
    return new GsonBuilder().setPrettyPrinting().create().toJson(schema);
  }

  static <T, S> Map<T, S> loadCachedSchema(URL cacheUrl) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader schemaReader =
        new BufferedReader(new InputStreamReader(cacheUrl.openStream()))) {
      String inputLine;
      while ((inputLine = schemaReader.readLine()) != null) {
        sb.append(inputLine).append('\n');
      }
    }

    return fromJson(sb.toString());
  }

  @SuppressWarnings("unchecked")
  private static <T, S> Map<T, S> fromJson(String json) {
    return new Gson().fromJson(json, HashMap.class);
  }

  /**
   * Specifies the version of the Kubernetes schema to use.
   *
   * @param version a Kubernetes version string, such as "1.9.0"
   * @throws IOException if no schema for that version is cached.
   */
  public void useKubernetesVersion(String version) throws IOException {
    KubernetesSchemaReference reference = KubernetesSchemaReference.create(version);
    URL cacheUrl = reference.getKubernetesSchemaCacheUrl();
    if (cacheUrl == null) {
      throw new IOException("No schema cached for Kubernetes " + version);
    }

    addExternalSchema(reference.getKubernetesSchemaUrl(), cacheUrl);
  }

  /**
   * Adds external schema.
   *
   * @param schemaUrl Schema URL
   * @param cacheUrl Cached URL
   * @throws IOException IO exception
   */
  public void addExternalSchema(URL schemaUrl, URL cacheUrl) throws IOException {
    Map<String, Map<String, Object>> objectObjectMap = loadCachedSchema(cacheUrl);
    Map<String, Object> definitions = objectObjectMap.get("definitions");
    for (Map.Entry<String, Object> entry : definitions.entrySet()) {
      if (!entry.getKey().startsWith("io.k8s.kubernetes.pkg.")) {
        schemaUrls.put(entry.getKey(), schemaUrl.toString());
      }
    }
  }

  /**
   * Specifies whether the "additionalProperties" property will be specified to forbid properties
   * not in the schema.
   *
   * @param forbidAdditionalProperties true to forbid unknown properties
   */
  public void setForbidAdditionalProperties(boolean forbidAdditionalProperties) {
    this.forbidAdditionalProperties = forbidAdditionalProperties;
  }

  /**
   * Specifies whether object fields will be implemented as references to existing definitions. If
   * false, nested objects will be described inline.
   *
   * @param supportObjectReferences true to reference definitions of object
   */
  public void setSupportObjectReferences(boolean supportObjectReferences) {
    this.supportObjectReferences = supportObjectReferences;
  }

  /**
   * Specifies whether top-level schema reference is included.
   *
   * @param includeSchemaReference true to include schema reference
   */
  public void setIncludeSchemaReference(boolean includeSchemaReference) {
    this.includeSchemaReference = includeSchemaReference;
  }

  /**
   * Suppress descriptions for fields from these packages.
   * @param packageName Package name
   */
  public void addPackageToSuppressDescriptions(String packageName) {
    this.suppressDescriptionForPackages.add(packageName);
  }

  /**
   * Generates an object representing a JSON schema for the specified class.
   *
   * @param someClass the class for which the schema should be generated
   * @return a map of maps, representing the computed JSON
   */
  public Map<String, Object> generate(Class<?> someClass) {
    Map<String, Object> result = new HashMap<>();

    if (includeSchemaReference) {
      result.put("$schema", JSON_SCHEMA_REFERENCE);
    }
    generateObjectTypeIn(result, someClass);
    if (!definedObjects.isEmpty()) {
      Map<String, Object> definitions = new TreeMap<>();
      result.put("definitions", definitions);
      for (Map.Entry<Class<?>, Object> entry : definedObjects.entrySet()) {
        if (!entry.getValue().equals(EXTERNAL_CLASS)) {
          definitions.put(getDefinitionKey(entry.getKey()), entry.getValue());
        }
      }
    }

    return result;
  }

  void generateFieldIn(Map<String, Object> map, Field field) {
    if (includeInSchema(field)) {
      map.put(getPropertyName(field), getSubSchema(field));
    }
  }

  private boolean includeInSchema(Field field) {
    return !isStatic(field) && !isVolatile(field) && !isDisabledFeature(field);
  }

  private boolean isStatic(Field field) {
    return Modifier.isStatic(field.getModifiers());
  }

  private boolean isVolatile(Field field) {
    return Modifier.isVolatile(field.getModifiers());
  }

  private boolean isDeprecated(Field field) {
    return field.getAnnotation(Deprecated.class) != null;
  }

  private boolean isDisabledFeature(Field field) {
    Feature feature = field.getAnnotation(Feature.class);
    return feature != null && !enabledFeatures.contains(feature.value());
  }

  private String getPropertyName(Field field) {
    SerializedName serializedName = field.getAnnotation(SerializedName.class);
    if (serializedName != null && serializedName.value().length() > 0) {
      return serializedName.value();
    } else {
      return field.getName();
    }
  }

  private Object getSubSchema(Field field) {
    Map<String, Object> result = new HashMap<>();

    SubSchemaGenerator sub = new SubSchemaGenerator(field);

    sub.generateTypeIn(result, field.getType());
    String description = getDescription(field);
    if (description != null) {
      result.put("description", description);
    }
    if (isDeprecated(field)) {
      result.put("deprecated", "true");
    }
    if (isString(field.getType())) {
      addStringRestrictions(result, field);
    } else if (isNumeric(field.getType())) {
      addRange(result, field);
    } else if (isMap(field.getType())) {
      sub.addMapValueType(result, field);
    }

    return result;
  }

  private boolean isString(Class<?> type) {
    return type.equals(String.class);
  }

  private boolean isDateTime(Class<?> type) {
    return type.equals(OffsetDateTime.class);
  }

  private boolean isNumeric(Class<?> type) {
    return Number.class.isAssignableFrom(type) || PRIMITIVE_NUMBERS.contains(type);
  }

  private boolean isMap(Class<?> type) {
    return Map.class.isAssignableFrom(type);
  }

  private String getDescription(Field field) {
    if (suppressDescriptionForPackages.contains(field.getDeclaringClass().getPackageName())) {
      return null;
    }
    Description description = field.getAnnotation(Description.class);
    if (description != null) {
      return description.value();
    }
    // ApiModelProperty is on the getter method
    String fieldName = field.getName();
    String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    try {
      Method getter = field.getDeclaringClass().getMethod(getterName);
      ApiModelProperty apiModelProperty = getter.getAnnotation(ApiModelProperty.class);
      String desc = apiModelProperty != null ? apiModelProperty.value() : null;
      if (isNullOrEmpty(desc)) {
        return getDescription(field.getType());
      }
      return desc;
    } catch (NoSuchMethodException e) {
      // no op
      return null;
    }
  }

  private String getDescription(Class<?> someClass) {
    if (suppressDescriptionForPackages.contains(someClass.getPackageName())) {
      return null;
    }
    Description description = someClass.getAnnotation(Description.class);
    if (description != null) {
      return description.value();
    }
    ApiModel apiModel = someClass.getAnnotation(ApiModel.class);
    return apiModel != null ? apiModel.description() : null;
  }

  private void addStringRestrictions(Map<String, Object> result, Field field) {
    Class<? extends Enum<?>> enumClass = getEnumClass(field);
    if (enumClass != null) {
      addEnumValues(result, enumClass, getEnumQualifier(field));
    }

    String pattern = getPattern(field);
    if (pattern != null) {
      result.put("pattern", pattern);
    }
  }

  private Class<? extends java.lang.Enum<?>> getEnumClass(Field field) {
    EnumClass annotation = field.getAnnotation(EnumClass.class);
    return annotation != null ? annotation.value() : null;
  }

  private String getEnumQualifier(Field field) {
    EnumClass annotation = field.getAnnotation(EnumClass.class);
    return annotation != null ? annotation.qualifier() : "";
  }

  private void addEnumValues(
      Map<String, Object> result, Class<? extends Enum<?>> enumClass, String qualifier) {
    result.put("enum", getEnumValues(enumClass, qualifier));
  }

  private String getPattern(Field field) {
    Pattern pattern = field.getAnnotation(Pattern.class);
    return pattern == null ? null : pattern.value();
  }

  private void addRange(Map<String, Object> result, Field field) {
    Range annotation = field.getAnnotation(Range.class);
    if (annotation == null) {
      return;
    }

    if (annotation.minimum() > Integer.MIN_VALUE) {
      result.put("minimum", annotation.minimum());
    }
    if (annotation.maximum() < Integer.MAX_VALUE) {
      result.put("maximum", annotation.maximum());
    }
  }

  private String getDefinitionKey(Class<?> type) {
    if (isDateTime(type)) {
      return "DateTime";
    }
    return type.getSimpleName();
  }

  private String[] getEnumValues(Class<?> enumType, String qualifier) {
    Method qualifierMethod = getQualifierMethod(enumType, qualifier);

    return Arrays.stream(enumType.getEnumConstants())
          .filter(constant -> satisfiesQualifier(constant, qualifierMethod))
          .filter(this::isNonObsolete)
          .map(Object::toString)
          .toArray(String[]::new);
  }

  private Method getQualifierMethod(Class<?> enumType, String methodName) {
    try {
      return Optional.of(enumType.getDeclaredMethod(methodName))
            .filter(this::isBooleanMethod)
            .orElse(null);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private boolean isBooleanMethod(Method method) {
    return method.getReturnType().equals(Boolean.class)
        || method.getReturnType().equals(boolean.class);
  }

  private boolean isNonObsolete(Object enumConstant) {
    return !(enumConstant instanceof Obsoleteable) || !((Obsoleteable) enumConstant).isObsolete();
  }

  private boolean satisfiesQualifier(Object enumConstant, Method qualifier) {
    try {
      return qualifier == null || (Boolean) qualifier.invoke(enumConstant);
    } catch (IllegalAccessException | InvocationTargetException e) {
      return true;
    }
  }

  private void generateObjectTypeIn(Map<String, Object> result, Class<?> type) {
    if (isDateTime(type)) {
      result.put(TYPE, STRING);
      result.put("format", "date-time");
    } else {
      result.put(TYPE, "object");
      if (forbidAdditionalProperties) {
        result.put("additionalProperties", "false");
      }
      Optional.ofNullable(getDescription(type)).ifPresent(s -> result.put("description", s));
      Optional.ofNullable(getPropertyFields(type)).ifPresent(f -> generateProperties(result, f));
    }
  }

  private void generateProperties(Map<String, Object> result, Collection<Field> propertyFields) {
    final Map<String, Object> properties = new HashMap<>();
    List<String> requiredFields = new ArrayList<>();
    result.put("properties", properties);

    for (Field field : propertyFields) {
      if (!isSelfReference(field)) {
        generateFieldIn(properties, field);
      }
      if (isRequired(field) && includeInSchema(field)) {
        requiredFields.add(getPropertyName(field));
      }
    }

    if (!requiredFields.isEmpty()) {
      result.put("required", requiredFields.toArray(new String[0]));
    }
  }

  private @Nullable Collection<Field> getPropertyFields(Class<?> type) {
    Set<Field> result = new LinkedHashSet<>();
    for (Class<?> cl = type; cl != null && !cl.equals(Object.class); cl = cl.getSuperclass()) {
      result.addAll(Arrays.asList(cl.getDeclaredFields()));
    }

    result.removeIf(this::isSelfReference);

    return result.isEmpty() ? null : result;
  }

  private boolean isSelfReference(Field field) {
    return field.getName().startsWith("this$");
  }

  private boolean isRequired(Field field) {
    return isPrimitive(field) || isNonNull(field);
  }

  private boolean isPrimitive(Field field) {
    return field.getType().isPrimitive();
  }

  private boolean isNonNull(Field field) {
    if (field.getAnnotation(Nonnull.class) != null) {
      return true;
    }

    // ApiModelProperty is on the getter method
    String fieldName = field.getName();
    String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    try {
      Method getter = field.getDeclaringClass().getMethod(getterName);
      ApiModelProperty apiModelProperty = getter.getAnnotation(ApiModelProperty.class);
      return apiModelProperty != null && apiModelProperty.required();
    } catch (NoSuchMethodException e) {
      // no op
      return false;
    }
  }

  public void defineAdditionalProperties(Class<?> forClass, String additionalPropertyType) {
    additionalPropertiesTypes.put(forClass, additionalPropertyType);
  }

  public void defineEnabledFeatures(Collection<String> enabledFeatures) {
    this.enabledFeatures.addAll(enabledFeatures);
  }

  private class SubSchemaGenerator {
    final Field field;

    SubSchemaGenerator(Field field) {
      this.field = field;
    }

    private void generateEnumTypeIn(Map<String, Object> result, Class<? extends Enum<?>> enumType) {
      result.put(TYPE, STRING);
      addEnumValues(result, enumType, "");
    }

    @SuppressWarnings("unchecked")
    private void generateTypeIn(Map<String, Object> result, Class<?> type) {
      if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) {
        result.put(TYPE, BOOLEAN);
      } else if (isNumeric(type)) {
        result.put(TYPE, NUMBER);
      } else if (isString(type)) {
        result.put(TYPE, STRING);
      } else if (type.isEnum()) {
        generateEnumTypeIn(result, (Class<? extends Enum<?>>) type);
      } else if (type.isArray()) {
        this.generateArrayTypeIn(result, type);
      } else if (Collection.class.isAssignableFrom(type)) {
        generateCollectionTypeIn(result);
      } else {
        generateObjectFieldIn(result, type);
      }
    }

    private void generateObjectFieldIn(Map<String, Object> result, Class<?> type) {
      if (supportObjectReferences) {
        generateObjectReferenceIn(result, type);
      } else {
        generateObjectTypeIn(result, type);
      }
    }

    private boolean addedKubernetesClass(Class<?> theClass) {
      if (!theClass.getName().startsWith("io.kubernetes.client")) {
        return false;
      }

      for (Map.Entry<String, String> entry : schemaUrls.entrySet()) {
        if (KubernetesApiNames.matches(entry.getKey(), theClass)) {
          definedObjects.put(theClass, EXTERNAL_CLASS);
          references.put(theClass, entry.getValue() + "#/definitions/" + entry.getKey());
          return true;
        }
      }

      return false;
    }

    private boolean isReferenceDefined(Class<?> type) {
      return definedObjects.containsKey(type) || addedKubernetesClass(type);
    }

    private void addReferenceIfNeeded(Class<?> type) {
      if (!isReferenceDefined(type)) {
        addReference(type);
      }
    }

    private void addReference(Class<?> type) {
      Map<String, Object> definition = new HashMap<>();
      definedObjects.put(type, definition);
      references.put(type, "#/definitions/" + getDefinitionKey(type));
      generateObjectTypeIn(definition, type);
    }

    private String getReferencePath(Class<?> type) {
      return references.get(type);
    }

    private void generateObjectReferenceIn(Map<String, Object> result, Class<?> type) {
      addReferenceIfNeeded(type);
      result.put("$ref", getReferencePath(type));
    }

    private void generateCollectionTypeIn(Map<String, Object> result) {
      Map<String, Object> items = new HashMap<>();
      result.put(TYPE, ARRAY);
      result.put(ITEMS, items);
      generateTypeIn(items, getGenericComponentType());
    }

    private Class<?> getGenericComponentType() {
      try {
        String typeName = field.getGenericType().getTypeName();
        String className = typeName.substring(typeName.indexOf("<") + 1, typeName.indexOf(">"));
        return field.getDeclaringClass().getClassLoader().loadClass(className);
      } catch (ClassNotFoundException e) {
        return Object.class;
      }
    }

    private void generateArrayTypeIn(Map<String, Object> result, Class<?> type) {
      Map<String, Object> items = new HashMap<>();
      result.put(TYPE, ARRAY);
      result.put(ITEMS, items);
      generateTypeIn(items, type.getComponentType());
    }

    private void addMapValueType(Map<String, Object> result, Field field) {
      final boolean savedForbidAdditionalProperties = forbidAdditionalProperties;
      addPreserveIfSpecified(result, field);
      forbidAdditionalProperties = false;
      try {
        Optional.ofNullable(getAdditionalPropertiesForMapField(field))
              .ifPresent(a -> result.put("additionalProperties", a));
      } finally {
        forbidAdditionalProperties = savedForbidAdditionalProperties;
      }
    }

    private void addPreserveIfSpecified(Map<String, Object> result, Field field) {
      Optional.ofNullable(field.getAnnotation(PreserveUnknown.class))
            .ifPresent(a -> result.put("x-kubernetes-preserve-unknown-fields", "true"));
    }

    private Map<String, Object> getAdditionalPropertiesForMapField(Field field) {
      final Class<?> mapValueType = getMapValueType(field);
      final String type = additionalPropertiesTypes.get(mapValueType);
      Map<String, Object> additionalProperties = new HashMap<>();
      if (type != null) {
        additionalProperties.put(TYPE, type);
      } else if (mapValueType == null || mapValueType.equals(String.class)) {
        generateTypeIn(additionalProperties, String.class);
      }
      return additionalProperties.isEmpty() ? null : additionalProperties;
    }

    private Class<?> getMapValueType(Field field) {
      return Optional.of(field.getGenericType())
            .map(this::asParameterizedType)
            .map(ParameterizedType::getActualTypeArguments)
            .map(this::actualValueType)
            .orElse(null);
    }

    private ParameterizedType asParameterizedType(Type type) {
      return type instanceof ParameterizedType ? (ParameterizedType) type : null;
    }

    private Class<?> actualValueType(Type[] types) {
      return (types.length < 2) ? null : (Class<?>) types[1];
    }
  }

  private static boolean isNullOrEmpty(String str) {
    return str == null || str.isEmpty();
  }
}
