    - [what](#sec-1)
    - [why](#sec-2)
    - [status](#sec-3)
    - [usage](#sec-4)
    - [how](#sec-5)
      - [defining resources -](#sec-5-1)
      - [state management](#sec-5-2)
      - [staging resources](#sec-5-3)
      - [diff](#sec-5-4)
      - [api functions](#sec-5-5)
      - [push](#sec-5-6)
      - [inconsistent](#sec-5-7)
      - [pull](#sec-5-8)
    - [acknowledgements](#sec-6)

# what<a id="sec-1" name="sec-1"></a>

almonds is a library / tool for dealing with CRUD of resources for a cloud provider. It operates in the same space that of AWS Cloud Formation and Hashicorp's Terraform. It combines the good parts of both, does away with the heartaches of both, and introduces features which are much more developer friendly.

# why<a id="sec-2" name="sec-2"></a>

I am a developer who used the above two tools for automating the infrastructure of large Fortune 500 company. However these tools have poor design decisions which make working with them furstrating, a sink hole for development hours, and make for unexpected surprises.

The current crop of toolings are like an hammer, and that also a buggy hammer if there ever was one ! Its very painfull to use these tools, and they make precise control and composition impossible.

As developers the least we need is powerfull and precise tools which can be tamed and moulded as we want and no more - more in the line of emacs and vim. almonds is an attempt to provide such a tool. almonds tries to provide a set of building blocks, fine grained operations, which can then be composed to form higher lavel of abstractions. It is an attempt to provide the same level of power and flexibility that we have while working with other types of apps to have with infrastructure elements.

1.  Set of primitives to deal with infrastructure resources.
2.  Fine grained control over application of these primitives to resources.
3.  A saner approach to state management.
4.  Concept of uniquenes for resources and ability to group them.
5.  API which can be used for composing operations as needed, or can be used to build applications over it.
6.  A thin layer over the AWS SDK to deal with its inconsistensies.
7.  No magic, its a powerful but dumb tool.

# status<a id="sec-3" name="sec-3"></a>

almonds is a very young tool and not production ready. It currently only provides for the CRUD of a few EC2 resources, but has plans to support all EC2 resources in next few months. It can be also extended to include resources from providers other than EC2, if there is sufficient interest. PR's / suggestions / criticisms are all very much welcome :)

almonds currently can be used a library in your clojure or JVM project. It will soon add the functionality of running from a command line and read in plain json (however there is no intent to add a DSL).

# usage<a id="sec-4" name="sec-4"></a>

It's not published on clojars/maven yet, so please clone the project and include it locally. It will be published once most of the basic EC2 resources are added.

# how<a id="sec-5" name="sec-5"></a>

## defining resources -<a id="sec-5-1" name="sec-5-1"></a>

-   Resources are defined as a hash map.
-   Each resource has two mandatory properties -

    :almonds-type :: this key denotes the type of the resource and has to be from one of the types defined by almonds. The CRUD behaviour of each resource is dependent opon this key.

    :almonds-tags :: this key is an array, and is used to uniquely identify that resource. The above :almonds-type is also added to the tags array when the resource is staged.
-   All other keys and values are those that correspond to the respective resource's AWS Java API.
-   The two almonds properties are saved as AWS tags, when the resources are created remotely. These two properties are again added to the resources when they are retrieved from AWS, and are critical to state management.
-   References are also defined in terms of :almonds-tags (ex In the above example, the vpc-id contains the value of the :almonds-tags of the vpc). These references are resolved before performing any operations remotely.

Below is an array of resources -

```clojure
(def my-resources [{:almonds-type :vpc
                    :almonds-tags [:sandbox :web-tier]
                    :cidr-block "10.2.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :vpc
                    :almonds-tags [:sandbox :app-tier]
                    :cidr-block "10.3.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :web-tier :web-server]
                    :cidr-block "10.2.11.0/25"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :web-tier]}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :app-tier :app-server]
                    :cidr-block "10.3.11.0/25"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :app-tier]}])
```

The above defines two vpcs and subnets, with each subnet having the vpc-id of the respective vpc.

## state management<a id="sec-5-2" name="sec-5-2"></a>

-   almonds maintains three atoms in the memory for managing state -
    -   **index:** this contains all the *staged* resources. Whenever a resource is staged it is added to the index.
    -   **pushed-state:** this contains only those resources that are avalaible remotely and have the almonds tags.
    -   **remote-state:** this contains all the resources that are avalaible remotely - almonds resources or not (this state is helpful during library development for debugging issues)
-   the **index** and the **pushed-state** are the source of truth. They both are used to determine the differential between the resources that are defined and the resources that exist remotely.

## staging resources<a id="sec-5-3" name="sec-5-3"></a>

-   The *almonds.api* namespace contains the api.
-   When resources are staged they are added to the local state.
-   Execution of the function on the REPL returns the :almonds-tags of all the resources that have been staged.
-   The :almonds-type was also aded to

```clojure
(require [almonds.api :all :refer])

(stage my-resources)


;; ==================>>>>>>>>>>>>>>>>>>>
;;
([:subnet :sandbox :app-tier :app-server]
 [:subnet :sandbox :web-tier :web-server]
 [:vpc :sandbox :app-tier]
 [:vpc :sandbox :web-tier])
;;
;; =====================================
```

## diff<a id="sec-5-4" name="sec-5-4"></a>

-   When the diff is run, it returns a differential between the **index** and the **pushed-state**.
-   It returns a hash-map with three keys -
    -   **:to-create:** these are the resources which have only been staged are not present remotely.
    -   **:to-delete:** these are the resources which are not staged but are present remotely *(Remember the state is transient, and if you staged the resourced from an REPL, and then created them, they will not be present in the staging state the next time you restart your REPL)*
    -   **:inconsistent:** these are resources which are present in *both* the staging state and pushed state and also *do not match*.
-   If the *pushed-state* is empty then the *pull* function is first called, which populates the *pushed-state* by retrieving resources from the remote end.

```clojure
(diff-tags)

;; ====================>>>>>>>>>>>>>>>>
;;
{:inconsistent (),
 :to-delete (),
 :to-create
 ([:sandbox :app-server :app-tier :subnet]
  [:sandbox :vpc :app-tier]
  [:web-tier :sandbox :web-server :subnet]
  [:web-tier :sandbox :vpc])}
;;
;; ====================================

(diff-tags :sandbox :vpc)

;; ====================>>>>>>>>>>>>>>>>
;;
{:inconsistent (),
 :to-delete (),
 :to-create ([:sandbox :vpc :app-tier]
             [:web-tier :sandbox :vpc])}
;;
;; ====================================

(diff :app-tier)

;; ====================>>>>>>>>>>>>>>>>
;;
{:to-create
 ({:almonds-tags [:subnet :sandbox :app-tier :app-server],
   :almonds-type :subnet,
   :availability-zone "us-east-1b",
   :vpc-id [:sandbox :app-tier],
   :cidr-block "10.3.11.0/25"}
  {:almonds-tags [:vpc :sandbox :app-tier],
   :almonds-type :vpc,
   :cidr-block "10.3.0.0/16",
   :instance-tenancy "default"}),
 :inconsistent (),
 :to-delete ()}
;;
;; =====================================
```

*Convention: All results of evaluation are presented as -*  **;; ==>**

## api functions<a id="sec-5-5" name="sec-5-5"></a>

-   All api functions are varaidic and can take zero to n number of tags.
-   All api functions have two variations ex - diff and diff-tags
    -   **diff:** displays the result in terms of the resource
    -   **diff-ids:** displays the resource in terms of the resource-ids
-   The ids variations are a convenience, and can be utilized when its not necessary to view the full resources.

## push<a id="sec-5-6" name="sec-5-6"></a>

-   The push function first performs a diff, and then calls the *create* and *delete* functions for the respective resources.
-   The resources under

```clojure
(push :app-tier)

;; ====================>>>>>>>>>>>>>>>>
;; the  below is printed on the console -
;;
;; Creating :vpc with :almonds-tags [:vpc :sandbox :app-tier]
;; Creating :subnet with :almonds-tags [:subnet :sandbox :app-tier :app-server]

;; ====================================
```

## inconsistent<a id="sec-5-7" name="sec-5-7"></a>

## pull<a id="sec-5-8" name="sec-5-8"></a>

# acknowledgements<a id="sec-6" name="sec-6"></a>

almonds uses the amazing [amazonica](https://github.com/mcohen01/amazonica) library to interact with the AWS Java SDK. Its rapid development would not have been possible without it and also thanks to its maintainers for rapidly addressing issues raised during the dveloment of almonds.

a big shout out to the whole clojure community, without which it would have been too cumbersome to write this tool.

a big thanks to the emacs community which makes the process of development so productive and fun.
