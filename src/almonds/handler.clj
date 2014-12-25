(ns almonds.handler
  (require [amazonica.aws.ec2 :refer :all]
           [slingshot.slingshot :refer [throw+]]
           [dire.core :refer [with-handler! with-wrap-hook!]]))

(def log-ec2-calls (atom false))

(def ec2-calls [#'accept-vpc-peering-connection 
                #'allocate-address
                #'assign-private-ip-addresses
                #'associate-address
                #'associate-dhcp-options
                #'associate-route-table
                #'attach-internet-gateway
                #'attach-network-interface
                #'attach-volume
                #'attach-vpn-gateway
                #'authorize-security-group-egress
                #'authorize-security-group-ingress
                #'bundle-instance
                #'cancel-bundle-task
                #'cancel-conversion-task
                #'cancel-export-task
                #'cancel-reserved-instances-listing
                #'cancel-spot-instance-requests
                #'confirm-product-instance
                #'copy-image
                #'copy-snapshot
                #'create-customer-gateway
                #'create-dhcp-options
                #'create-image
                #'create-instance-export-task
                #'create-internet-gateway
                #'create-key-pair
                #'create-network-acl
                #'create-network-acl-entry
                #'create-network-interface
                #'create-placement-group
                #'create-reserved-instances-listing
                #'create-route
                #'create-route-table
                #'create-security-group
                #'create-snapshot
                #'create-spot-datafeed-subscription
                #'create-subnet
                #'create-tags
                #'create-volume
                #'create-vpc
                #'create-vpc-peering-connection
                #'create-vpn-connection
                #'create-vpn-connection-route
                #'create-vpn-gateway
                #'delete-customer-gateway
                #'delete-dhcp-options
                #'delete-internet-gateway
                #'delete-key-pair
                #'delete-network-acl
                #'delete-network-acl-entry
                #'delete-network-interface
                #'delete-placement-group
                #'delete-route
                #'delete-route-table
                #'delete-security-group
                #'delete-snapshot
                #'delete-spot-datafeed-subscription
                #'delete-subnet
                #'delete-tags
                #'delete-volume
                #'delete-vpc
                #'delete-vpc-peering-connection
                #'delete-vpn-connection
                #'delete-vpn-connection-route
                #'delete-vpn-gateway
                #'deregister-image
                #'describe-account-attributes
                #'describe-addresses
                #'describe-availability-zones
                #'describe-bundle-tasks
                #'describe-conversion-tasks
                #'describe-customer-gateways
                #'describe-dhcp-options
                #'describe-export-tasks
                #'describe-image-attribute
                #'describe-images
                #'describe-instance-attribute
                #'describe-instance-status
                #'describe-instances
                #'describe-internet-gateways
                #'describe-key-pairs
                #'describe-network-acls
                #'describe-network-interface-attribute
                #'describe-network-interfaces
                #'describe-placement-groups
                #'describe-regions
                #'describe-reserved-instances
                #'describe-reserved-instances-listings
                #'describe-reserved-instances-modifications
                #'describe-reserved-instances-offerings
                #'describe-route-tables
                #'describe-security-groups
                #'describe-snapshot-attribute
                #'describe-snapshots
                #'describe-spot-datafeed-subscription
                #'describe-spot-instance-requests
                #'describe-spot-price-history
                #'describe-subnets
                #'describe-tags
                #'describe-volume-attribute
                #'describe-volume-status
                #'describe-volumes
                #'describe-vpc-attribute
                #'describe-vpc-peering-connections
                #'describe-vpcs
                #'describe-vpn-connections
                #'describe-vpn-gateways
                #'detach-internet-gateway
                #'detach-network-interface
                #'detach-volume
                #'detach-vpn-gateway
                #'disable-vgw-route-propagation
                #'disassociate-address
                #'disassociate-route-table
                #'dry-run
                #'enable-vgw-route-propagation
                #'enable-volume-io
                #'get-console-output
                #'get-password-data
                #'import-instance
                #'import-key-pair
                #'import-volume
                #'modify-image-attribute
                #'modify-instance-attribute
                #'modify-network-interface-attribute
                #'modify-reserved-instances
                #'modify-snapshot-attribute
                #'modify-subnet-attribute
                #'modify-volume-attribute
                #'modify-vpc-attribute
                #'monitor-instances
                #'purchase-reserved-instances-offering
                #'reboot-instances
                #'register-image
                #'reject-vpc-peering-connection
                #'release-address
                #'replace-network-acl-association
                #'replace-network-acl-entry
                #'replace-route
                #'replace-route-table-association
                #'report-instance-status
                #'request-spot-instances
                #'reset-image-attribute
                #'reset-instance-attribute
                #'reset-network-interface-attribute
                #'reset-snapshot-attribute
                #'revoke-security-group-egress
                #'revoke-security-group-ingress
                #'run-instances
                #'start-instances
                #'stop-instances
                #'terminate-instances
                #'unassign-private-ip-addresses])

(defmacro add-exception-handler [handler]
  `(do
     (with-handler! ~handler
       com.amazonaws.AmazonClientException
       (fn [e# & args#]
         (throw+ {:type ::aws-call-exception
                  :aws-fn ~handler
                  :args args#
                  :error e#})))))

(defmacro add-logging-hook [handler]
  `(do
     (with-wrap-hook! ~handler
       (fn [result# args#]
         (when @log-ec2-calls
           (do (println (str "Calling " ~handler " with input : " (print-str args#)))
               (println (str "The above call returned : " (print-str result#)))))))))

(doseq [calls ec2-calls]
  (add-exception-handler calls))

(doseq [calls ec2-calls]
  (add-logging-hook calls))
