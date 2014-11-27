;; (ns almonds.route-table
;;   (:require [amazonica.aws.ec2 :as aws-ec2]
;;             [almonds.resource :as r :refer [Resource]]))

;; (defrecord RouteTable [id-tag cidr routes vpc]
;;   Resource
;;   (retrieve-raw-all [this]
;;     (:route-tables (aws-ec2/describe-route-tables)))
;;   (tf [this]
;;     (r/to-json {"resource"
;;                 {"aws_route_table"
;;                  {id-tag
;;                   {"vpc_id" (r/tf-id vpc)
;;                    "cidr_block" cidr}}}}))
;;   (tf-id [this]
;;     (str "${aws_route_table." id-tag ".id}"))
;;   r/RouteTable
;;   (route-propogation? [this virtual-private-gateway]
;;     (let[route-table (r/retrieve-resource this)
;;          vpg-aws-id (r/aws-id virtual-private-gateway)]
;;       (-> route-table
;;           :propagating-vgws
;;           (r/has-value? :gateway-id vpg-aws-id)))))
