(ns almonds.state)

(def local-state (atom {}))
(def remote-state (atom {}))

(def create-sequence
  [:customer-gateway :vpc :security-group :subnet :network-acl :network-acl-entry :network-acl-association])

(def delete-sequence
  [:security-group :subnet :network-acl-association :network-acl-entry :network-acl :vpc :customer-gateway])

(def already-retrieved-remote? (atom false))

;;;;;;;;;;;;;;; clear states ;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-all []
  (reset! already-retrieved-remote? false)
  (doseq [state [local-state remote-state]]
    (reset! state {})))

(defn clear-remote-state []
  (reset! remote-state {}))

(def set-already-retrieved-remote #(reset! already-retrieved-remote? true))
