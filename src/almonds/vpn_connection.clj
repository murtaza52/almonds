(ns almonds.vpn-connection
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [Resource is-up? retrieve-raw-all retrieve-resource]]))

(defrecord VpnConnection [id-tag]
  Resource
  (retrieve-raw-all [this]
    (:vpn-connections (aws-ec2/describe-vpn-connections)))
  r/VpnConnection
  (is-up? [this]
    (-> this
         retrieve-resource
         :vgw-telemetry
         (r/has-value? :status "UP")))
  (is-static? [this]
    (-> this
        retrieve-resource
        :options
        :static-routes-only
        true?))
  (has-route? [this route]
    (when
        (->> this
             retrieve-resource
             :routes
             (filter (fn[{:keys [destination-cidr-block]}] (= destination-cidr-block route)))
             seq)
      true)))

(comment
  (is-up? (->VpnConnection "CentralVpcVpnConnectionPrimary"))

  (def a  (-> (->VpnConnection "CentralVpcVpnConnectionPrimary")
              retrieve-resource
              :vgw-telem)))
