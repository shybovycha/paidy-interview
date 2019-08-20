require 'sinatra'
require 'json'
require 'date'

DATA = {
  'AUDUSD' => 0.75,
  'AUDEUR' => 0.25
}

get '/quotes' do
  pairs = params['pairs'].split(',')
  api_key = params['api_key']

  halt 400 if api_key.nil? or api_key.empty?

  pairs.map { |p| puts ">> #{p.inspect} = #{DATA[p].inspect}"; { symbol: p, price: DATA[p], timestamp: Time.now.to_i } }.to_json
end

