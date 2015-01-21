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

(comment (filter-resources @remote-state :security-group))
(comment (filter-resources nil))

(defn ec2-classic? [m]
  (if (= (:domain m) "standard") true false))

(ec2-classic? {:domain "standarda"})


