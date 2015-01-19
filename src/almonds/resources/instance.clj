(ns almonds.resources.instance
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.state :refer :all]))

(defmethod validate :instance [m]
  true)

(defn prepare-request [m]
  (as-> m m
    (update-in m [:security-group-ids] #(map aws-id %))
    (dissoc m :almonds-tags :almonds-type)))

(defmethod create :instance [m]
  (let [request (prepare-request m)
        response (aws-ec2/run-instances request)
        id (-> response :reservation :instances first :instance-id)]
    (create-aws-tags id m)))

(defmethod parent-type :instance [_]
  nil)

(defmethod sanitize :instance [m]
  (-> m
      (dissoc :almonds-aws-id :monitoring :root-device-type :private-dns-name :hypervisor :architecture :root-device-name :virtualization-type :product-codes :ami-launch-index :state-transition-reason :network-interfaces :kernel-id :public-dns-name :private-ip-address :placement :client-token :public-ip-address :block-device-mappings :state :tags :instantce-id :launch-time)
      (update-in [:security-groups] 
                 (fn[groups]
                   (map #(-> % :group-id aws-id->almonds-tags) groups)))))

(defmethod retrieve-all :instance [_]
  (->> (aws-ec2/describe-instances)
       vals
       first
       (mapcat :instances)))

(comment (retrieve-all {:almonds-type :instance}))

(defmethod delete :instance [m]
  (aws-ec2/delete-security-group {:group-id (aws-id (:almonds-tags m))}))

(defmethod aws-id-key :instance [_]
  :instance-id)

(defmethod pre-staging :instance [m]
  (as-> m m
    (update-in m
               [:security-group-ids]
               (fn[groups]
                 (map #(add-type :security-group %) groups)))
    (if-not (:min-count m) (assoc m :min-count 1) m)
    (if-not (:max-count m) (assoc m :max-count 1) m)))

(defmethod get-default-dependents :instance [m]
  [])

(defmethod is-dependent? :instance [_]
  false)

(defmethod dependent-types :instance [_]
  [])

(defmethod dependents :instance [m]
  [])
