import React, {useState, useEffect, useRef} from 'react';


const Logs = ({initialOutput}) => {
    const [terminalOutput, setTerminalOutput] = useState('');
    const terminalOutputRef = useRef(null);

    useEffect(() => {
        if (initialOutput && Array.isArray(initialOutput)) {
            if (initialOutput.length > 0) setTerminalOutput(initialOutput.join('\n')); else setTerminalOutput("Waiting for another message...\n")
        }
        if (terminalOutputRef.current) {
            terminalOutputRef.current.scrollTop = terminalOutputRef.current.scrollHeight;
        }
    }, [initialOutput]);


    return (

        <div className="border border-black p-4 w-full h-[50vh]" ref={terminalOutputRef}>
            <pre className={"absolute"}>{terminalOutput}</pre>
        </div>
    );
};

export default Logs;
