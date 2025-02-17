+++
title = "Usage"
date = 2020-03-11T16:45:16-05:00
weight = 20
pre = "<b> </b>"
description = "Steps for creating and deploying Model in Image images and their associated Domain YAML files."
+++

This document describes what's needed to create and deploy a typical Model in Image domain.

#### Contents

   - [WebLogic Kubernetes Operator](#weblogic-kubernetes-operator)
   - [WebLogic Server image](#weblogic-server-image)
   - [Directory structure](#directory-structure)
   - [Supplying initial model files and WDT](#supplying-initial-model-files-and-wdt)
   - [Optional WDT model ConfigMap](#optional-wdt-model-configmap)
   - [Required runtime encryption secret](#required-runtime-encryption-secret)
   - [Secrets for model macros](#secrets-for-model-macros)
   - [Domain fields](#domain-fields)
   - [Always use external state](#always-use-external-state)
   - [Requirements for JRF domain types](#requirements-for-jrf-domain-types)

#### WebLogic Kubernetes Operator

Deploy the operator and ensure that it is monitoring the desired namespace for your Model in Image domain. See [Manage operators]({{< relref "/userguide/managing-operators/_index.md" >}}) and [Quick Start]({{< relref "/quickstart/_index.md" >}}).

#### WebLogic Server image

Model in Image requires an image with a WebLogic Server installation.

- You can start with a WebLogic Server 12.2.1.3 or later Oracle Container Registry pre-built base image such as `container-registry.oracle.com/middleware/weblogic:12.2.1.3` for WLS domains or `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3` for JRF domains. For an example of this approach for both WLS and JRF domains, see the [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}) sample. For detailed instructions on how to log in to the Oracle Container Registry and accept the license agreement for an image (required to allow pulling an Oracle Container Registry image), see this [document]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}}).

- Or, you can manually build your own base image, as described in [Preparing a Base Image]({{< relref "/userguide/base-images/_index.md#create-a-custom-image-with-patches-applied" >}}). This is useful if you want your base images to include additional patches. Note that any 12.2.1.3 image must also include patch 29135930 (the pre-built images already contain this patch).

### Directory structure

Model in Image requires the following directory structure in its pods for
its (optional) WDT model artifacts and (required) WDT binaries:

| Domain resource attribute  | Default directory          | Contents                              |
| -------------------------- | -------------------------- | ------------------------------------- |
| `domain.spec.configuration.model.modelHome` | `/u01/wdt/models` | Zero or more model `.yaml`, `.properties`, and/or archive `.zip` files.|
| `domain.spec.configuration.model.wdtInstallHome` | `/u01/wdt/weblogic-deploy` | Unzipped WDT installation binaries (required).  |

### Supplying initial model files and WDT

Model in Image minimally requires an image with a WebLogic installation
(see [WebLogic Server image](#weblogic-server-image)), plus access
to:
* A WDT installation in `domain.spec.configuration.model.wdtInstallHome`.
* One or more WDT model `.yaml` files that configure your domain in
  the `domain.spec.configuration.model.modelHome` directory
  or in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).
* Zero or more WDT model `.properties` files in the
  `domain.spec.configuration.model.modelHome` directory
  or in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).
* Zero or more WDT model application `.zip` archives
  in the `domain.spec.configuration.model.modelHome` directory. Archives
  must be supplied in the model home because application archives
  are not supported in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).

There are multiple methods for supplying Model in Image WDT artifacts:

  - __Include in main image__:
    You can include the artifacts in your domain resource `domain.spec.image`
    in its `domain.spec.configuration.model.modelHome`
    and `domain.spec.configuration.model.wdtInstallHome` directories as
    a layer on top of your base image
    (where the base image includes your WebLogic installation).

    Use either of the following methods.

    - _Manual image creation_ uses Docker commands to layer the WDT artifacts, described in the previous table,
      on top of your base image into a new image.
    - The _WebLogic Image Tool_ (WIT) has built-in options for layering WDT model files,
      WDT binaries, WebLogic Server binaries, and WebLogic Server patches in an image.
      The [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}) sample uses the WIT approach.

  - __Use auxiliary images__:
    Use [auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}})
    to create one or more small images that contain the desired files. This automatically copies files
    from each of the small images into each pod's file system's
    `configuration.model.modelHome` or `configuration.model.wdtInstallHome` location.

  - __Use a Persistent Volume Claim (PVC)__:
    This method is for advanced use cases only. Supply WDT model YAML, variable, or archive files
    in a [Persistent Volume Claim]({{< relref "/faq/volumes.md" >}})
    and modify `configuration.model.modelHome` and `configuration.model.wdtInstallHome` to
    the corresponding directory within the PVC's mount location.

  - __Use a WDT model ConfigMap__:
    Use the [Optional WDT model ConfigMap](#optional-wdt-model-configmap) for
    WDT model YAML and `.properties` files. This can be combined with
    any of the previously mentioned methods and is most often used to facilitate runtime
    updates to models supplied by one of these methods.

For more information about model file syntax,
see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

#### Optional WDT model ConfigMap

You can create a WDT model ConfigMap that defines additional model `.yaml` and `.properties` files beyond what you've already supplied in your image, and then reference this ConfigMap using your Domain YAML file's `configuration.model.configMap` attribute. This is optional if the supplied image already fully defines your model.

WDT model ConfigMap files will be merged with the WDT files defined in your image at runtime before your domain home is created. The ConfigMap files can add to, remove from, or alter the model configuration that you supplied within your image.

For example, place additional `.yaml` and `.properties` files in a directory called `/home/acmeuser/wdtoverride` and run the following commands:

  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    create configmap MY-DOMAINUID-my-wdt-config-map \
    --from-file /home/acmeuser/wdtoverride
  ```
  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    label  configmap MY-DOMAINUID-my-wdt-config-map \
    weblogic.domainUID=MY-DOMAINUID
  ```

See [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) for a discussion of model file syntax and loading order, and see [Runtime updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) for a discussion of using WDT model ConfigMaps to update the model configuration of a running domain.


### Required runtime encryption secret

Model in Image requires a runtime encryption secret with a secure `password` key. This secret is used by the operator to encrypt model and domain home artifacts before it adds them to a runtime ConfigMap or log. You can safely change the `password`, at any time after you've fully shut down a domain, but it must remain the same for the life of a running domain. The runtime encryption secret that you create can be named anything, but note that it is a best practice to name and label secrets with their domain UID to help ensure that cleanup scripts can find and delete them.

**NOTE**: Because the runtime encryption password does not need to be shared and needs to exist only for the life of a domain, you may want to use a password generator.

Example:

  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    create secret generic MY-DOMAINUID-runtime-encrypt-secret \
    --from-literal=password=welcome1
  ```
  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    label secret MY-DOMAINUID-runtime-encrypt-secret \
    weblogic.domainUID=MY-DOMAINUID
  ```

Corresponding Domain YAML file snippet:

  ```yaml
  configuration:
    model:
      runtimeEncryptionSecret: MY-DOMAINUID-runtime-encrypt-secret
  ```

#### Secrets for model macros

Create additional secrets as needed by macros in your model files. For example, these can store database URLs and credentials that are accessed using `@@SECRET` macros in your model that reference the secrets.  For a discussion of model macros, see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

#### Domain fields

The following Domain fields are specific to Model in Image domains.

| Domain Resource Attribute                    | Notes |
| -------------------------                    | ------------------ |
| `domainHomeSourceType`                       | Required. Set to `FromModel`. |
| `domainHome`                                 | Must reference an empty or non-existent directory within your image. Do not include the mount path of any persistent volume. Note that Model in Image recreates the domain home for a WebLogic Server pod every time the pod restarts.|
| `configuration.model.configMap`              | Optional. Set if you have stored additional models in a ConfigMap as per [Optional WDT model ConfigMap](#optional-wdt-model-configmap). |
| `configuration.secrets`                      | Optional. Set this array if your image or ConfigMap models contain macros that reference custom Kubernetes Secrets. For example, if your macros depend on secrets `my-secret` and `my-other-secret`, then set to `[my-secret, my-other-secret]`.|
| `configuration.model.runtimeEncryptionSecret`| Required. All Model in Image domains must specify a runtime encryption secret. See [Required runtime encryption secret](#required-runtime-encryption-secret). |
| `configuration.model.domainType`             | Set the type of domain. Valid values are `WLS`, `JRF`, and `RestrictedJRF`, where `WLS` is the default. See [WDT Domain Types](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools-config/domain_def/).|
| `configuration.model.runtimeEncryptionSecret`| Required. All Model in Image domains must specify a runtime encryption secret. See [Required runtime encryption secret](#required-runtime-encryption-secret). |
| `configuration.model.modelHome`              | Optional. Location of the WDT model home, which can include model YAML files, `.properties` files, and application `.zip` archives. Defaults to `/u01/wdt/models`.|
| `configuration.model.wdtInstallHome`         | Optional. Location of the WDT install. Defaults to `/u01/wdt/weblogic-deploy`.|

**Notes**:

 - There are additional attributes that are common to all domain home source types, such as the `image` field. See the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md) and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}) for a full list of Domain fields.

 - There are also additional fields that are specific to JRF domain types. For more information, see [Requirements for JRF domain types](#requirements-for-jrf-domain-types).

 - For fully specified Model in Image Domain YAML file examples,
   see the operator source directory
   `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources`
   for the [Model in Image sample]({{< relref "/samples/domains/model-in-image/_index.md" >}}).
   The `WLS` and `JRF` subdirectories in this directory correspond to the `configuration.model.domainType`.

#### Always use external state

Regardless of the domain home source type, we recommend that you always keep state outside the image. This includes cluster database leasing tables, JMS and transaction stores, EJB timers, and so on. This ensures that data will not be lost when a container is destroyed.

We recommend that state be kept in a database to take advantage of built-in database server high availability features, and the fact that disaster recovery of sites across all but the shortest distances, almost always requires using a single database server to consolidate and replicate data (DataGuard).

For more information see:
- [Tuning JDBC Stores](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/perfm/storetune.html#GUID-F868624F-2898-4330-AF96-FC0EAB3CFE0A) in _Tuning Performance of Oracle WebLogic Server_.
- [Using a JDBC Store](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/fusion-middleware/weblogic-server/12.2.1.4/perfm&id=STORE-GUID-328FD4C1-91C4-4901-B1C4-97B9D93B2ECE) in _Administering the WebLogic Persistent Store_.
- [High Availability Best Practices](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/jmsad/best_practice.html#GUID-FB97F9A2-E6FA-4C51-B74E-A2A5DDB43B8C) in _Administering JMS Resources for Oracle WebLogic Server_.
- [Leasing](https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/fusion-middleware/weblogic-server/12.2.1.4/jmsad&id=CLUST-GUID-8F7348E2-7C45-4A6B-A72C-D1FB51A8E83F) in _Administering Clusters for Oracle WebLogic Server_.

#### Requirements for JRF domain types

{{% notice info %}} This section applies only for a `JRF` domain type. Skip it if your domain type is `WLS` or `RestrictedJRF`.
{{% /notice %}}

A JRF domain requires an infrastructure database, initializing this database using RCU, and configuring your domain to access this database. All of these steps must occur before you first deploy your domain. When you first deploy your domain, the introspector job will initialize it's OPSS schema tables in the database - a process that can take several minutes.

Furthermore, if you want to safely ensure that a restarted JRF domain can access updates to the infrastructure database that the domain made at an earlier time, the original domain's wallet file must be safely saved as soon as practical, and the restarted domain must be supplied a wallet file that was obtained from a previous run of the domain.

##### JRF Domain YAML file and model YAML file settings

Here are the required Domain YAML file and model YAML file settings for Model in Image JRF domains:

- Set `configuration.model.domainType` to `JRF`.

- Set `configuration.opss.walletPasswordSecret` to reference a secret that defines a `walletPassword` key. This is used to encrypt the domain's OPSS wallet file. This is a required field for JRF domains.

- Set `configuration.opss.walletFileSecret` to reference a secret that contains your domain's OPSS wallet file in its `walletFile` key. This assumes you have an OPSS wallet file from a previous start of the same domain. It enables a restarted or migrated domain to access its database information. This is an optional field for JRF domains, but must always be set if you want a restarted or migrated domain to access its database information.

- Set the `configuration.introspectorJobActiveDeadlineSeconds` introspection job timeout to at least 600 seconds. This is in an optional field but is needed because domain home creation takes a considerable amount of time the first time a JRF domain is created (due to initializing the domain's database tables), and because Model in Image creates your domain home for you using the introspection job.

- Define an `RCUDbInfo` stanza in your model. Access to an database requires defining a `RCUDbInfo` stanza in your model's `domainInfo` stanza with the necessary information for accessing the domain's schema within the database. Usually this information should be supplied using a secret that you deploy and reference in your Domain YAML file's `configuration.secrets` field. Here's an example `RCUDbInfo` stanza:

  ```yaml
  domainInfo:
      RCUDbInfo:
          rcu_prefix:          '@@SECRET:sample-domain1-rcu-access:rcu_prefix@@'
          rcu_schema_password: '@@SECRET:sample-domain1-rcu-access:rcu_schema_password@@'
          rcu_db_conn_string:  '@@SECRET:sample-domain1-rcu-access:rcu_db_conn_string@@'

  ```

##### Saving and restoring JRF wallets

It is important to save a JRF domain's OPSS wallet password and wallet file so that you can restore them as needed. This ensures that a restart or migration of the domain can continue to access the domain's FMW infrastructure database.

When you deploy a JRF domain for the first time, the domain will add itself to its RCU database tables, and also create a 'wallet' file in the domain's home directory that enables access to the domain's data in the RCU database. This wallet is encrypted using an OPSS key password that you supply to the domain using a Secret that is referenced by your Domain YAML file `configuration.opss.walletPasswordSecret` field.

For a domain that has been started by Model in Image, the operator will copy the wallet file from the domain home of a new JRF domain and store it in the domain's introspector domain ConfigMap in file `ewallet.p12`. Here is how to export this wallet file from the introspector domain ConfigMap:

- Option 1
  ```shell
  $ kubectl -n MY_DOMAIN_NAMESPACE \
    get configmap MY_DOMAIN_UID-weblogic-domain-introspect-cm \
    -o jsonpath='{.data.ewallet\.p12}' \
    > ewallet.p12
  ```
- Option 2

  Alternatively, you can use the `./kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/opss-wallet.sh -s` command to export the wallet file (pass `-?` to this script's command-line arguments and defaults).

{{% notice tip %}}
Always back up your wallet file to a safe location that can be retrieved later. In addition, save your OPSS key password.
{{% /notice %}}

To reuse the wallet:
  - Create a secret with a key named `walletPassword` that contains the same OPSS password that you specified in the original domain. For example, assuming the password is `welcome1`:
    ```shell
    $ kubectl -n MY_DOMAIN_NAMESPACE \
      create secret generic MY_DOMAIN_UID-my-opss-wallet-password-secret \
      --from-literal=walletPassword=welcome1
    ```
    ```shell
    $ kubectl -n MY_DOMAIN_NAMESPACE \
      label secret MY_DOMAIN_UID-my-opss-wallet-password-secret \
      weblogic.domainUID=sample-domain1
    ```
  - Create a secret with a key named `walletFile` that contains the OPSS wallet file that you exported above. For example, assuming the file is `ewallet.p12`:
    ```shell
    $ kubectl -n MY_DOMAIN_NAMESPACE \
      create secret generic MY_DOMAIN_UID-my-opss-wallet-file-secret \
      --from-file=walletFile=ewallet.p12
    ```
    ```shell
    $ kubectl -n sample-domain1-ns \
      label secret MY_DOMAIN_UID-my-opss-wallet-file-secret \
      weblogic.domainUID=sample-domain1
    ```
    Alternatively, you can use the `./kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/opss-wallet.sh -r` command to deploy a local wallet file as a secret (pass `-?` to get this script's command-line arguments and defaults).
  - Make sure that your Domain YAML file `configuration.opss.walletPasswordSecret` field names the OPSS password Secret, and make sure that your Domain YAML file `configuration.opss.walletFileSecret` field names the OPSS wallet file secret.


##### Instructions for changing a JRF domain's database password

Follow these steps to ensure that a JRF domain can continue to access its RCU data after changing its database password.

- Before changing the database password, shut down all domains that access the database schema. For example, set their `serverStartPolicy` to `NEVER`.

- Update the password in the database.

- Update the Kubernetes Secret that contains your `RCUDbInfo.rcu_schema_password` for each domain.

- Restart the domains. For example, change their `serverStartPolicy` from `NEVER` to `IF_NEEDED`.

- Save your wallet files again, as changing your password generates a different wallet.

##### JRF references

For an example of using JRF in combination with Model in Image, see the [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}) sample.

See also, [Specifying RCU connection information in the model](https://github.com/oracle/weblogic-deploy-tooling/blob/main/documentation/1.9/content/rcuinfo.md) in the WDT documentation.
