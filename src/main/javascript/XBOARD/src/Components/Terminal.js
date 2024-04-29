import React, {useState, useEffect} from 'react';
import './Terminal.css'; // Import CSS file with terminal styles

const Terminal = ({initialOutput}) => {
    const [terminalOutput, setTerminalOutput] = useState('');
    const [inputValue, setInputValue] = useState('');

    useEffect(() => {
        if (initialOutput && Array.isArray(initialOutput)) {
            setTerminalOutput(initialOutput.join('\n'));
        }
    }, [initialOutput]);

    const handleInputChange = (event) => {
        if (event.key === 'Enter') {
            const command = event.target.value.trim();
            setInputValue('');

            if (command === '') {
                updateTerminal(prompt);
                return;
            }

            if (command.toLowerCase() === 'clear') {
                setTerminalOutput('');
                return;
            }
            if (command.toLowerCase().startsWith('send')) {
                updateTerminal(prompt + command)
                return;
            }
            if (command.toLowerCase() === 'help') {
                const helpMessage = `Available Commands:
- clear: Clear the terminal screen.
- send: Send a message to the network table server
- help: Show available commands and their descriptions.
`;
                updateTerminal(`${prompt}${command}\n${helpMessage}`);
                return;
            }

            updateTerminal(`${prompt}${command}\nCommand not recognized: ${command}`);
        }
    };

    const updateTerminal = (output) => {
        setTerminalOutput(prevOutput => prevOutput + `\n${output}`);
    };

    const prompt = '$ ';

    return (
        <div className="">
            <div className="terminal border border-black p-4 w-full">
                <pre id="terminal-output">{terminalOutput}</pre>
                <pre className="terminal-input-output"></pre>
                <div className="flex">
                    <span className="terminal-input flex-none mr-2">{prompt}</span>
                    <input
                        id="terminal-input"
                        type="text"
                        className="flex-grow bg-transparent focus:outline-none"
                        value={inputValue}
                        onChange={(event) => setInputValue(event.target.value)}
                        onKeyDown={handleInputChange}
                    />
                </div>
            </div>
        </div>
    );
};

export default Terminal;
