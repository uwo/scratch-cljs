(ns frontend.app
  ;; Used https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers
  (:require [cljs.core.async :as a]
            [cognitect.transit :as t]))

(def req-chan (a/chan))

(def res-chan (a/chan))

(defonce twriter (t/writer :json))
(defonce treader (t/reader :json))

(defn post-message
  [worker message]
  (.postMessage worker (t/write twriter message)))

(defn start-queuing-loop
  "Enforces response order by preventing a queued request from being
   sent to the worker until the worker finishes with the previous request"
  [worker req-chan res-chan]
  (prn "Starting queuing loop")
  (a/go-loop []
    (when-let [req (a/<! req-chan)]
      (let [result-chan (:result-chan req)]
        (post-message worker (dissoc req :result-chan))
        (when-let [res (a/<! res-chan)]
          ;; put response on the promise-chan provided by the requester:
          (prn "handling response" res)
          (a/>! result-chan res)
          (recur))))))

(defn start-queuing-loop2
  [worker req-chan res-chan]
  (prn "Starting queuing loop 2")
  (a/go-loop [order-n 0
              requests-by-order-n (sorted-map)
              responses-by-order-n (sorted-map)]
    (let [[the-val the-chan] (a/alts! [req-chan res-chan])]
      ;; FIXME: dotimes, e.g. 10, isn't finishing all jobs
      (cond
        ;; A job was requested:
        (and (= the-chan req-chan) (some? the-val))
        (let [_ (prn "A job was requested:" order-n)
              result-chan (:result-chan the-val)
              msg (-> the-val
                      (dissoc :result-chan)
                      (assoc :order-n order-n))]
          (post-message worker msg)
          (recur (inc order-n)
                 (assoc requests-by-order-n order-n result-chan)
                 responses-by-order-n))
        ;; A job completed:
        (and (= the-chan res-chan) (some? the-val))
        (let [res-order-n (:order-n the-val)
              _ (prn "A job completed:" res-order-n)
              next-order-n (ffirst requests-by-order-n)
              _ (prn "next-order-n" next-order-n)
              responses-by-order-n (assoc responses-by-order-n res-order-n the-val)
              _ (prn "responses-by-order-n" responses-by-order-n)
              ready-responses (subseq responses-by-order-n <= next-order-n)
              _ (prn "ready-responses" ready-responses)
              ready-ns (mapv first ready-responses)]
          (doseq [[n response] ready-responses]
            (let [result-chan (get requests-by-order-n n)]
              (a/>! result-chan (dissoc response :order-n))))
          (recur order-n
                 (apply dissoc requests-by-order-n ready-ns)
                 (apply dissoc responses-by-order-n ready-ns)))))))

(defn start-queuing-loop3
  [worker req-chan res-chan]
  (prn "Starting queuing loop 3")
  (a/go-loop [order-n 0
              requests-by-order-n (sorted-map)
              responses-by-order-n (sorted-map)]
    (let [[the-val the-chan] (a/alts! [req-chan res-chan])]
      (cond
        ;; A job was requested:
        (and (= the-chan req-chan) (some? the-val))
        (let [_ (prn "A job was requested:" order-n)
              result-chan (:result-chan the-val)
              msg (-> the-val
                      (dissoc :result-chan)
                      (assoc :order-n order-n))]
          (post-message worker msg)
          (recur (inc order-n)
                 (assoc requests-by-order-n order-n result-chan)
                 responses-by-order-n))
        ;; A job completed:
        (and (= the-chan res-chan) (some? the-val))
        (let [res-order-n (:order-n the-val)
              next-order-n (ffirst requests-by-order-n)
              responses-by-order-n (assoc responses-by-order-n res-order-n the-val)
              {:keys [reqs ress]} (loop [n next-order-n
                                         reqs requests-by-order-n
                                         ress responses-by-order-n]
                                    ;; Check if there's a response for the next-n:
                                    (if-let [response (get responses-by-order-n n)]
                                      (let [result-chan (get requests-by-order-n n)]
                                        (a/>! result-chan (dissoc response :order-n))
                                        ;; See if the next one has already completed:
                                        (recur (inc n)
                                               (dissoc reqs n)
                                               (dissoc ress n)))
                                      ;; Can't post any more results, return where we left off
                                      {:reqs reqs
                                       :ress ress}))]
          (recur order-n reqs ress))))))

(defn request-job
  ;; Queue the request to enforce order, as opposed to calling
  ;; post-message on the worker directly.
  [req-chan job]
  (let [result-chan (a/promise-chan)]
    (a/put! req-chan (assoc job :result-chan result-chan))
    result-chan))

(defn create-worker
  [{:keys [worker-file res-chan req-chan]}]
  (let [worker (js/Worker. worker-file)]
    (.addEventListener worker "message" (fn [e] (a/put! res-chan (t/read treader (.. e -data)))))
    (request-job req-chan {:type :init :data "some init data"})
    worker))

(defn init []
  (println "Hello World")
  ;; Uncomment to test on refresh:
  #_(let [worker (create-worker {:worker-file "/js/worker.js"
                                 :req-chan req-chan
                                 :res-chan res-chan})]
      (start-queuing-loop worker req-chan res-chan)
    (request-job req-chan {:type :validate :data "hello world"})))

(comment
  (def worker
    (create-worker {:worker-file "/js/worker.js"
                    :req-chan req-chan
                    :res-chan res-chan}))

  (start-queuing-loop worker req-chan res-chan)

  (start-queuing-loop2 worker req-chan res-chan)

  (start-queuing-loop3 worker req-chan res-chan)

  ;; Do something with the result:
  (a/go
    (let [res (a/<! (request-job req-chan {:type :validate :data "hello world"}))]
      (prn "do something with result" res)))

  ;; Note that handling of responses is in order:
  (dotimes [n 10]
    (a/go
      (let [res (a/<! (request-job req-chan {:type :validate :data "hello world" :n n}))]
        (prn "do something with result" res))))

  (def sm
    (into (sorted-map) {1 {:data 'abc :order-n 1}
                        2 {:data 'def :order-n 2}
                        3 {:data 'ghi :order-n 3}
                        4 {:data 'jkl :order-n 4}}))
  (ffirst sm)

  (subseq sm <= 2) ;; => ([1 {:data abc, :order-n 1}] [2 {:data def, :order-n 2}])

  )
