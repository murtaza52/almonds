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

(defn drop-from-remote-state [almonds-type]
  (swap! remote-state
         (fn[coll]
           (remove
            (fn[resource]
              (= almonds-type (:almonds-type resource)))
            coll))))

(comment (drop-from-remote-state :vpc))

(defn add-keys [resource]
  (-> resource
      add-almonds-keys
      add-almonds-aws-id))

(comment (add-keys {:group-id "sg-49619b24",
                    :group-name "Security_Group; 2; Classic",
                    :ip-permissions [{:ip-protocol "tcp", :from-port 7015, :to-port 7015, :user-id-group-pairs [], :ip-ranges ["27.0.0.0/0"]}],
                    :tags
                    [{:value "#{:security-group 2 :classic}", :key ":almonds-tags"}
                     {:value "Security_Group; 2; Classic", :key "Name"}
                     {:value ":security-group", :key ":almonds-type"}],
                    :description "Security_Group; 2; Classic",
                    :owner-id "790378854888",
                    :ip-permissions-egress []}))

(defn instance-state? [state]
  (fn [m]
    (if (= state (-> m :state :name)) true false)))

(def is-terminated? (instance-state? "terminated"))

(def is-running? (instance-state? "running"))

(comment  (is-terminated? {:state {:name "terminated"}})
          (is-terminated? {:s {:name "terminated"}}))
