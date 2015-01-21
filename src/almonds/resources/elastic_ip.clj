(ns almonds.resources.elastic-ip
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.api-utils :refer :all]
            [almonds.state :refer :all]))

(defmethod validate :elastic-ip [m]
  true)

(defmethod create :elastic-ip [m]
  (aws-ec2/allocate-address m))

;; create a new address
;; {:almonds-type :eip :count 5}
;; put in the address when it is created
;; {:almonds-type :eip :address "abc"}
;; associate an address
;; {:almonds-type :address-allocation :instance-id "ab" :eip "abc"}

(defmethod parent-type :elastic-ip [_]
  nil)

(defmethod sanitize :elastic-ip [m]
  m)

(defmethod retrieve-all :elastic-ip [_]
  (let [ips (-> (aws-ec2/describe-addresses) :addresses)]
    (map (fn[m]
           (as-> m m
             (assoc m :almonds-type :elastic-ip) ;; adding the almonds-type here as it is not supported by aws
             (assoc m :almonds-tags #{:elastic-ip (:public-ip m)})))
         ips)))

(defmethod delete :elastic-ip [m]
  (println "Cannot delete an elastic-ip as can not ascertain its identity. Please use the delete-elastic-ip to explicitly delete it."))

(defn delete-elastic-ip [m]
  (if (ec2-classic? m)
    (aws-ec2/release-address {:public-ip (:public-ip m)})
    (aws-ec2/release-address {:allocation-id (:allocation-id m)})))

(defmethod pre-staging :elastic-ip [m]
  (as-> m m
    (if-not (:domain m) (assoc m :domain "standard") m)))

(defmethod get-default-dependents :elastic-ip [m]
  [])

(defmethod is-dependent? :elastic-ip [_]
  false)

(defmethod dependent-types :elastic-ip [_]
  [:eip-assoc])

(defmethod dependents :elastic-ip [m]
  (if (ec2-classic? m)
    (when-not (= "" (:instance-id m))
      (as-> m v
        (assoc v :almonds-type :eip-assoc)
        (update-in v [:instance-id] aws-id->almonds-tags) ;;needed for preparing the almonds-tags
        (prepare-almonds-tags v)  
        (assoc v :instance-id (:instance-id m)))) ;; restoring the instance-id which will be now replaced during sanitize
    (when (:association-id m)
      m)))

(comment (retrieve-dependents '({:almonds-tags #{:elastic-ip "107.22.188.118"}, :almonds-type :elastic-ip, :domain "standard", :instance-id "i-059641f4", :public-ip "107.22.188.118"})))
