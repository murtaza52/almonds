    - [what](#sec-1)
    - [why](#sec-2)
    - [status](#sec-3)
    - [usage](#sec-4)
      - [Releases](#sec-4-1)
      - [Credentials](#sec-4-2)

# what<a id="sec-1" name="sec-1"></a>

*almonds* is a library for realizing the ideal of infrastructure as code. It operates in the same space that of AWS's Cloud Formation and Hashicorp's Terraform. It takes inspiration from both, tries to address their shortcomings, and introduces features which are developer friendly.

# why<a id="sec-2" name="sec-2"></a>

The current tools have poor designs, which makes working with them frustrating, they are a sink hole for development hours, and their unpredictable behavour springs up nasty surprises.

Moreover there is space for a tool which provides the following -

-   fine grained operations that can be applied to resources
-   extension points for introducing new resources (can be provider independent)
-   ability to specify identity of each resource
-   precise way of grouping and excluding resources for applying operations
-   a stateless approach to state management
-   no magic, no framework, only a small dumb library

# status<a id="sec-3" name="sec-3"></a>

*almonds* is a very young tool and you will encounter bugs. It currently only provides for the CRUD of a few EC2 resources, but has plans to support all EC2 resources in next few months. It can be also extended to include resources from providers other than EC2, if there is sufficient interest. PR's / suggestions / criticisms are all very much welcome :)

It can be used as a library in your clojure or JVM project. In near future you would also be able to run it from the command line with json as input. However the json will be plain vanilla json and no DSL will be added. The json can be generated using your preferred language.

# usage<a id="sec-4" name="sec-4"></a>

## Releases<a id="sec-4-1" name="sec-4-1"></a>

1.  The library is hosted on [clojars](https://clojars.org/almonds).
2.  To use it as a [leiningen](http://github.com/technomancy/leiningen/) dependency -

```clojure
[almonds "0.2.2"]
```

1.  To use it as a [maven](http://maven.apache.org/) dependency -

```clojure
<dependency>
  <groupId>almonds</groupId>
  <artifactId>almonds</artifactId>
  <version>0.2.2</version>
</dependency>
```

## Credentials<a id="sec-4-2" name="sec-4-2"></a>

In your code set the aws credentials - 

```clojure
(require [almonds.core :refer [set-aws-credentials]])

(set-aws-credentials "aws-access-key" "aws-secret" "https://ec2.amazonaws.com")
```
