(defproject almonds "0.2.3"
  :description "A library for infrastructure automation"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :url "https://github.com/murtaza52/almonds"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [amazonica "0.3.4"]
                 [slingshot "0.10.3"]
                 [dire "0.5.3"]
                 [prismatic/plumbing "0.3.3"]
                 [prismatic/schema "0.2.6"]
                 [camel-snake-kebab "0.2.4"]
                 [org.clojure/data.json "0.2.5"]
                 [environ "1.0.0"]
                 [org.clojure/tools.trace "0.7.8"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]}})
