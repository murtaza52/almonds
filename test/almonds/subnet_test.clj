(ns almonds.subnet-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.subnet :as s]
            [almonds.resource :as r]
            [almonds.vpc :as vpc]))

(facts "Subnet Resource"
  (fact "tf returns tf json"
    (r/tf (s/->Subnet "my-sub" "1-a" "1.1.1.1/12" (vpc/->VPC "my-vpc"))) => "{\"resource\":{\"aws_subnet\":{\"my-sub\":{\"vpc_id\":\"${aws_vpc.my-vpc.id}\",\"cidr_block\":\"1.1.1.1/12\",\"availability_zone\":\"1-a\"}}}}"))
