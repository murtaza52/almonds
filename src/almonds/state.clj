(ns almonds.state)

(def local-state (atom {}))
(def remote-state (atom {}))

(def create-sequence
  [:customer-gateway :vpc :security-group :security-rule :subnet :network-acl :network-acl-entry :network-acl-association :instance :elastic-ip :eip-assoc])

(def pull-sequence
  [:customer-gateway :vpc :security-group :subnet :network-acl :instance :elastic-ip])

(def delete-sequence
  [:instance :security-rule :security-group :subnet :network-acl-association :network-acl-entry :network-acl :vpc :customer-gateway :eip-assoc :elastic-ip])

(def already-retrieved-remote? (atom false))

(def set-already-retrieved-remote #(reset! already-retrieved-remote? true))

(def verbose-mode-state (atom false))

(defn verbose-mode? [] @verbose-mode-state)

(def aws-creds (atom {}))

(def stack (atom :default))

(def aws-bucket-name (atom ""))

(defn get-stack [] @stack)
