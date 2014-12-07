(defproject almonds "0.2.0"
  :description "A library for AWS automation"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [amazonica "0.3.4"]
                 [org.clojure/core.match "0.2.1"]
                 [slingshot "0.10.3"]
                 [dire "0.5.2"]
                 [prismatic/plumbing "0.3.3"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [prismatic/schema "0.2.6"]
                 [cheshire "5.3.1"]
                 [camel-snake-kebab "0.2.4"]
                 [org.clojure/data.json "0.2.5"]
                 [environ "1.0.0"]
                 [midje "1.6.3"]]
  :plugins [[lein-environ "1.0.0"]
            [lein-midje "3.1.3"]])
