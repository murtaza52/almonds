# almonds - a lib for infrastructure automation

*almonds* is an effort to realize the ideal of _infra as code_.


## almonds design

    1 Identity - Every resource can be uniquely identified using a unique collection of tags, or an operation can be performed on a group of resources using a common set of tags.
    2 A saner state model - ability to recreate the state from remote and locally defined resources.
    3 A consistent API - almonds provides a pose of function which can be applied to resources in a similar manner.

## almonds - usage

    - Define your resources

    ``` clojure

(def my-resources [{:almonds-type :vpc
                    :almonds-tags [:sandbox :web-tier 1]
                    :cidr-block "10.2.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :subnet
                    :almonds-tags [:s 1]
                    :cidr-block "10.2.11.0/25"
                    :availability-zone "us-east-1b"
                    :vpc-id #{1 :web-tier :sandbox :vpc}}

                   {:almonds-type :subnet
                    :almonds-tags [:s 2]
                    :cidr-block "10.2.13.128/25"
                    :availability-zone "us-east-1b"
                    :vpc-id #{2 :web-tier :sandbox :vpc}}])

    ```
    The above defines three resources. Each resource has two required properties `:almonds-type` and `:almonds-tags`. The _almonds-type_ is a property that defines the type of the resource, and is needed by almonds to decide how to deal with the resource. The almonds-tags is a set of tags which uniquely defines each resource. No two resources can have the same set of tags (the value of the :almonds-type is appended to the array of :almonds-tags when the resource is staged)

    In the subnet, the vpc-id is refrenced by the :almonds-tags of the vpc. All references are defined using the :almonds-tags.

    When creating the resources the :almonds-tags and :amonds-type are added as 'tags' in AWS. This enables _almonds_ to uniquely identify resources.

    The keys and values in the above hash-maps are the params that are required by the AWS Java SDK, and can be gleaned from the java docs. _almonds_ uses the _amzonica_ library underneath to transact all operations with the Java SDK.

    - Stage them

    ``` clojure
    (require [almonds.api :refer :all]) ;; Importing all the functions in the api namespace. This will be common to all operations shown hereafter.

    (stage my-resources)
    ```
    When the resources are staged they are validated and added to the local state. The list of validation functions is custom defined for each resource type.

    - Push them
    ```
    (push)

    ```
    - Lets add a new subnet, delete and change an existing one.
    ```clojure
    (def my-resources [{:almonds-type :vpc
                        :almonds-tags [:sandbox :web-tier 1]
                        :cidr-block "10.2.0.0/16"
                        :instance-tenancy "default"}

                       {:almonds-type :subnet
                        :almonds-tags [:s 1]
                        :cidr-block "10.2.11.0/25"
                        :availability-zone "us-east-1c"
                        :vpc-id #{1 :web-tier :sandbox :vpc}}

                       {:almonds-type :subnet
                        :almonds-tags [:s 2]
                        :cidr-block "10.2.13.128/25"
                        :availability-zone "us-east-1b"
                        :vpc-id #{3 :web-tier :sandbox :vpc}}])

      ```
      In the above the avalaibility zone of the first subnet has been changed.

      The second subnet has been replaced by a new one. Notice their almonds-tags, they are different.
