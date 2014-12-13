(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [midje.repl :refer [autotest load-facts]]
            [almonds.core]))
(autotest :pause)
