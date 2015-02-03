(defproject almonds "0.3.2"
  :description "A library for infrastructure automation"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :url "https://github.com/murtaza52/almonds"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [amazonica "0.3.13"]
                 [slingshot "0.10.3"]
                 [dire "0.5.3"]
                 [prismatic/plumbing "0.3.3"]
                 [prismatic/schema "0.2.6"]
                 [camel-snake-kebab "0.3.0" :exclusions [org.clojure/clojure]]
                 [environ "1.0.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [circuit-breaker "0.1.7"]]
  :bin {:name "almonds"
        :bin-path "~/bin"}
  :profiles {:uberjar {:aot :all}}
  :omit-source false
  :main ^:skip-aot almonds.runner)
