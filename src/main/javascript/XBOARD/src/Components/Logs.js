import React, { useState, useEffect, useRef } from 'react';
import './Terminal.css'; // Import CSS file with terminal styles

const Logs = ({ initialOutput }) => {
    const [terminalOutput, setTerminalOutput] = useState('');
    const terminalOutputRef = useRef(null);

    useEffect(() => {
        if (initialOutput && Array.isArray(initialOutput)) {
            setTerminalOutput(initialOutput.join('\n'));
        }
        if (terminalOutputRef.current) {
            terminalOutputRef.current.scrollTop = terminalOutputRef.current.scrollHeight;
        }
    }, [initialOutput]);



    return (
        <div className="terminal border border-black p-4 w-full h-[50vh] overflow-auto" ref={terminalOutputRef}>
            <pre>{terminalOutput}</pre>
            <pre className="terminal-input-output"></pre>
        </div>
    );
};

export default Logs;
