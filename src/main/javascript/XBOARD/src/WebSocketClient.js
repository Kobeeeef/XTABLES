import { useEffect, useState } from 'react';

const WebSocketClient = () => {
    const [jsonValues, setJsonValues] = useState({});

    useEffect(() => {
        const socket = new WebSocket('ws://localhost:8080'); // WebSocket server address

        socket.onopen = () => {
            console.log('Connected to WebSocket server');
        };

        socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            // Update JSON values based on the received message
            setJsonValues(message);
        };

        socket.onclose = () => {
            console.log('Disconnected from WebSocket server');
        };

        return () => {
            socket.close();
        }
    }, []);

    return (
        <div>
            {/* Render JSON values */}
            <pre>{JSON.stringify(jsonValues, null, 2)}</pre>
        </div>
    );
};

export default WebSocketClient;
