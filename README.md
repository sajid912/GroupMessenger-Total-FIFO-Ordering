# GroupMessenger2-Distributed Systems
Implemented total ordering across messenger instances that preserves FIFO ordering

This project will focus on:
• Implementing a FIFO total ordering algorithm.
• Detect and handle failure of a single messenger instance.

The app provides a total ordering guarantee for incoming messages among all app instances 
and that total ordering preserves FIFO ordering (total-FIFO ordering).

The app uses Basic multicast. It does not implement Reliable multicast.

The project implements modified ISIS algorithm to provide total-FIFO ordering under a single process failure.
It can handle at most one failure of an app instance during execution. Also, we are implementing a decentralized algorithm.

Every app instance cleans up the state related to a failed node and then moves on.

Every message is stored in the content provider on the local device individually by each app instance. 
Each message is stored as a key-value pair, with the key being the final delivery sequence number for the message (as a Java string) and the
value being the actual message (as a Java string). 
The delivery sequence number for this purpose starts with 0 and increases by 1 for each message.
