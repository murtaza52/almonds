(ns almonds.api-utils
  (require [almonds.state :refer :all]
           [almonds.utils :refer :all]))

(defn take-pull? []
  (not (or (seq @remote-state) @already-retrieved-remote?)))

(defn filter-resources [coll & args]
  (doall
   (filter (fn [{:keys [almonds-tags]}]
             (contains-set? (into #{} almonds-tags)
                            (into #{} args)))
           coll)))

(comment (filter-resources @remote-state))
(comment (filter-resources nil))


