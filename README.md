
# Table of Contents

[what](#sec-1)
[why](#sec-2)
    - [status](#sec-3)
    - [usage](#sec-4)
      - [Releases](#sec-4-1)
      - [Credentials](#sec-4-2)
    - [how](#sec-5)
      - [defining resources](#sec-5-1)
      - [state management](#sec-5-2)
      - [staging resources](#sec-5-3)
      - [diff](#sec-5-4)
      - [api functions](#sec-5-5)
      - [push](#sec-5-6)
      - [modifying resources](#sec-5-7)
      - [recreating resources](#sec-5-8)
    - [acknowledgements](#sec-6)


<a name="sec-1"></a>
# what

*almonds* is a library for realizing the ideal of infrastructure as code. It operates in the same space that of AWS's Cloud Formation and Hashicorp's Terraform. It takes inspiration from both, tries to address their shortcomings, and introduces features which are developer friendly.

# why<a id="sec-2" name="sec-2"></a>

There are few problems associated with the current crop of tools - 

-   State Management 
    -   **Terraform:** this is the Achille's heel of terraform. It maintains state in a file which is its source of truth. This file has to be shared/synced when multiple developers are working simultaneously, and is a pain. The scenario (though unlikely) of the state file being irrecoverable will be disastrous.
    -   **CloudFormation:** cloudformation does a much better job, it maintains identity of each resource, and recreates the state every time it is run. However there are horror stories of its state getting corrupted during failed updates, and the only way out is to call in the AWS support team.
    -   **Almonds:** almonds borrows cloudformation's way of specifying the identity of resources. However this functionality is explicit and user can choose to set the identity. The state is also always recreated and is explicit to the user, who can inspect both the local state and the remote state. The user can also diff between the local and remote state's (an idea borrowed from terraform), and even compare individual resources between state's.
-   Unpredictability  
    -   **Terraform and CloudFormation:** both try to be /"intelligent"/ by calculating the dependencies of resources, and then performing CRUD on them too. This leads to unwanted behaviour and nasty surprises. Example - If the security group of an instance is changed, then both the tools will **also** delete and recreate the instance. A simple update can turn into a nightmare. You never know which operation will succeed and which will fail.
    -   **Almonds:** almonds is /"dumb"/, it leaves the intelligence to the user. It only performs the specific operation on the specific group of resources that are specified. It will not perform those operations on the dependencies by default, but this can be specified too. All operations are explicit and there are no surprises.
-   DSL 
    -   **Terraform and CloudFormation:** both of them provide an external DSL to specify resources. CloudFormation has a minimal one while terraform provides an extensive one. IMHO this is a major mistake, Terraform's DSL is a pain to use, and looking at the DSL it seems they are trying to recreate a programming language, albiet a very clunky one.
    -   **Almonds:** almonds will not provide an external DSL. Currently it can be used in you JVM projects as a library. It will provide limited command line functionality where the input will be a plain json file. Your favourite programming language can be used to generate the json if needed.
-   Coarse Grained
    -   **Terraform and CloudFormation:** both of these are hammers, and your every operation better be a nail. Your context is a file/folder, and all crud operations will be applied on all resources in that context.
    -   **Almonds:** it provides a very fine grained mechanism to group resources, and allows control over what operations should be run and in what order. Its a library, which provides a set of functions to deal with resources. These functions can now be composed to form new abstractions. This is perhaps the most important differentiator, almonds is a library.

# status<a id="sec-3" name="sec-3"></a>

*almonds* is a very young tool and you will encounter bugs. It currently only provides for the CRUD of a few EC2 resources, but has plans to support all EC2 resources in next few months. It can be also extended to include resources from providers other than EC2, if there is sufficient interest. PR's / suggestions / criticisms are all very much welcome :)

It can be used as a library in your clojure or JVM project. In near future you would also be able to run it from the command line with json as input. However the json will be plain vanilla json and no DSL will be added. The json can be generated using your preferred language.

# usage<a id="sec-4" name="sec-4"></a>

## Releases<a id="sec-4-1" name="sec-4-1"></a>

-   The library is hosted on [clojars](https://clojars.org/almonds).
-   To use it as a [leiningen](http://github.com/technomancy/leiningen/) dependency -

```clojure
[almonds "0.2.3"]
```

-   To use it as a [maven](http://maven.apache.org/) dependency -

```clojure
<dependency>
  <groupId>almonds</groupId>
  <artifactId>almonds</artifactId>
  <version>0.2.3</version>
</dependency>
```

## Credentials<a id="sec-4-2" name="sec-4-2"></a>

In your code set the aws credentials - 

```clojure
(require [almonds.core :refer [set-aws-credentials]])

(set-aws-credentials "aws-access-key" "aws-secret" "https://ec2.amazonaws.com")
```

# how<a id="sec-5" name="sec-5"></a>

## defining resources<a id="sec-5-1" name="sec-5-1"></a>

-   Resources are defined as a hash map.
-   Each resource has two mandatory properties -
    -   **:almonds-type:** this key denotes the type of the resource and has to be from one of the types defined by almonds. The CRUD behaviour of each resource is dependent opon this key.
    -   **:almonds-tags:** this key is an array, and is used to uniquely identify that resource. The above :almonds-type is also added to the tags array when the resource is staged.
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
    -   **staging-state:** this contains all the *staged* resources. Whenever a resource is staged it is added to the staging-state.
    -   **pushed-state:** this contains only those resources that are avalaible remotely and have the almonds tags.
    -   **remote-state:** this contains all the resources that are avalaible remotely - almonds resources or not (this state is helpful during library development for debugging issues)
-   the **staging-state** and the **pushed-state** are the source of truth. They both are used to determine the differential between the resources that are defined and the resources that exist remotely.

## staging resources<a id="sec-5-3" name="sec-5-3"></a>

-   The *almonds.api* namespace contains the api.
-   When resources are staged they are added to the local state.
-   Execution of the function on the REPL returns the :almonds-tags of all the resources that have been staged.
-   The :almonds-type is added to the :almonds-tags vector of each resource.

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

-   When the diff is run, it returns a differential between the **staging-state** and the **pushed-state**.
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
-   The *push* function like other in the api can also be invoked with specific
-   The resources under the :inconsistent key are not affected.
-   The *pull* function is called after the respective resources have been added/deleted.

```clojure
(push :app-tier)

;; ====================>>>>>>>>>>>>>>>>
;; the  below is printed on the console -
;;
;; Creating :vpc with :almonds-tags [:vpc :sandbox :app-tier]
;; Creating :subnet with :almonds-tags [:subnet :sandbox :app-tier :app-server]

;; ====================================
```

## modifying resources<a id="sec-5-7" name="sec-5-7"></a>

-   When an existing resource is changed locally or remotely it will appear under the :inconsistent key.
-   In the example below the :cidr-block of both the vpc and subnet have been changed.
-   The *diff* shows both of these under the :inconsistent key.
-   Below they are recreated using the *recreate* function.

```clojure
(def app-tier [{:almonds-type :vpc
                :almonds-tags [:sandbox :app-tier]
                :cidr-block "10.4.0.0/16"
                :instance-tenancy "default"}

               {:almonds-type :subnet
                :almonds-tags [:sandbox :app-tier :app-server]
                :cidr-block "10.4.0.0/26"
                :availability-zone "us-east-1b"
                :vpc-id [:sandbox :app-tier]}])

(stage app-tier)

(diff)

;; ====================>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
;;
{:to-create (),
 :inconsistent
 ({:almonds-tags [:subnet :sandbox :app-tier :app-server],
   :almonds-type :subnet,
   :availability-zone "us-east-1b",
   :vpc-id [:vpc :sandbox :app-tier],
   :cidr-block "10.4.0.0/26"}
  {:almonds-tags [:vpc :sandbox :app-tier], :almonds-type :vpc, :cidr-block "10.4.0.0/16", :instance-tenancy "default"}),
 :to-delete ()}
;;
;; =================================================

(recreate :app-tier)

;; ====================>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
;;
;; Deleting :subnet with :almonds-tags [:subnet :sandbox :app-tier :app-server]
;; Deleting :vpc with :almonds-tags [:vpc :sandbox :app-tier]
;; Creating :vpc with :almonds-tags [:vpc :sandbox :app-tier]
;; {:almonds-tags [:vpc :sandbox :app-tier], :almonds-type :vpc, :cidr-block 10.4.0.0/16, :instance-tenancy default}
;; Creating :subnet with :almonds-tags [:subnet :sandbox :app-tier :app-server]
;; {:almonds-tags [:subnet :sandbox :app-tier :app-server], :almonds-type :subnet, :availability-zone us-east-1b, :vpc-id [:vpc :sandbox :app-tier], :cidr-block
;;
;; =================================================
```

## recreating resources<a id="sec-5-8" name="sec-5-8"></a>

-   There are four different ways in which the above could have been achieved
    -   **(recreate :app-tier):** calling the function without the tag would have recreated all the resources.
    -   **(recreate-inconsistent):** this will run the diff first and the recreate *all* resources that are inconsistent. If a tag is used then, then a diff will be run with the tag, thus limiting which inconsistent resources are recreated.
    -   **(delete-resources :app-tier) (stage app-tier) (push):** this will first delete the :app-tier resources, then stage them and then create them.
    -   **(unstage :app-tier) (push) (stage app-tier) (push):** this will unstage the resources (remove them from staging-state), then push deletes them, then stage the resources, and then pull creates them.
-   The higher level functions are a combination of the more granular functions, however the granular ones can be used as needed.

# acknowledgements<a id="sec-6" name="sec-6"></a>

almonds uses the amazing [amazonica](https://github.com/mcohen01/amazonica) library to interact with the AWS Java SDK. Its rapid development would not have been possible without it and also thanks to its maintainers for rapidly addressing issues raised during the dveloment of almonds.

a big shout out to the whole clojure community, without which it would have been too cumbersome to write this tool.

a big thanks to the emacs community which makes the process of development so productive and fun.
