;; (ns almonds.virtual-private-gateway
;;   (:require [amazonica.aws.ec2 :as aws-ec2]
;;             [almonds.resource :as r :refer [Resource retrieve-resource retrieve-all retrieve-raw exists?]]))

;; (defrecord VirtualPrivateGateway [almonds-tags]
;;   Resource
;;   (retrieve-all [this]
;;     (:vpn-gateways (aws-ec2/describe-vpn-gateways)))
;;   (aws-id [this]
;;     (-> this
;;         retrieve-resource
;;         :vpn-gateway-id))
;;   r/VirtualPrivateGateway
;;   (is-attached? [this]
;;     (-> this
;;         retrieve-resource
;;         :vpc-attachments
;;         first
;;         :state
;;         (= "attached"))))
